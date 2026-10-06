use std::net::SocketAddr;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use anyhow::Context;
use pumpkin_data::packet::CURRENT_MC_VERSION;
use pumpkin_protocol::codec::var_int::VarInt;
use pumpkin_protocol::java::client::config::{
    CConfigDisconnect, CConfigPing, CFinishConfig, CKnownPacks,
};
use pumpkin_protocol::java::client::login::{CLoginDisconnect, CLoginSuccess, CSetCompression};
use pumpkin_protocol::java::client::play::{CKeepAlive, CPlayerPosition};
use pumpkin_protocol::java::packet_decoder::TCPNetworkDecoder;
use pumpkin_protocol::java::packet_encoder::TCPNetworkEncoder;
use pumpkin_protocol::java::server::config::{SAcknowledgeFinishConfig, SConfigPong, SKnownPacks};
use pumpkin_protocol::java::server::handshake::SHandShake;
use pumpkin_protocol::java::server::login::{SLoginAcknowledged, SLoginStart};
use pumpkin_protocol::java::server::play::{
    SChatCommand, SConfirmTeleport, SKeepAlive, SPlayerLoaded, SPlayerPosition, SSwingArm,
};
use pumpkin_protocol::ser::NetworkWriteExt;
use pumpkin_protocol::{ClientPacket, ConnectionState, MultiVersionJavaPacket, ServerPacket};
use pumpkin_util::math::vector3::Vector3;
use tokio::io::{BufReader, BufWriter};
use tokio::net::TcpStream;
use tokio::net::tcp::{OwnedReadHalf, OwnedWriteHalf};
use tokio::sync::Mutex;
use tracing::{debug, trace};
use uuid::Uuid;

#[allow(dead_code)]
pub struct BotClient {
    pub name: String,
    pub uuid: Uuid,
    network_writer: Arc<Mutex<TCPNetworkEncoder<BufWriter<OwnedWriteHalf>>>>,
    network_reader: Arc<Mutex<TCPNetworkDecoder<BufReader<OwnedReadHalf>>>>,
    pub in_play: Arc<AtomicBool>,
    pub closed: Arc<AtomicBool>,
    failure: Mutex<Option<String>>,
    last_received: Mutex<Option<(ConnectionState, i32)>>,
}

impl BotClient {
    fn write_packet<P: ClientPacket>(packet: &P) -> Result<Vec<u8>, anyhow::Error> {
        let mut buf = Vec::new();
        buf.write_var_int(&VarInt(P::to_id(CURRENT_MC_VERSION)))?;
        packet.write_packet_data(&mut buf, &CURRENT_MC_VERSION)?;
        Ok(buf)
    }

    pub async fn send_packet<P: ClientPacket>(&self, packet: &P) -> anyhow::Result<()> {
        let buf = Self::write_packet(packet)?;
        let mut encoder = self.network_writer.lock().await;
        encoder.write_packet(buf.into()).await?;
        encoder.flush().await?;
        Ok(())
    }

    pub async fn connect(address: SocketAddr, name: &str) -> anyhow::Result<Arc<Self>> {
        let stream = TcpStream::connect(address).await?;
        let (read_half, write_half) = stream.into_split();
        let writer = Arc::new(Mutex::new(TCPNetworkEncoder::new(BufWriter::new(
            write_half,
        ))));
        let reader = Arc::new(Mutex::new(TCPNetworkDecoder::new(BufReader::new(
            read_half,
        ))));

        let bot = Arc::new(Self {
            name: name.to_string(),
            uuid: Uuid::new_v4(),
            network_writer: writer,
            network_reader: reader,
            in_play: Arc::new(AtomicBool::new(false)),
            closed: Arc::new(AtomicBool::new(false)),
            failure: Mutex::new(None),
            last_received: Mutex::new(None),
        });

        debug!("[BOT] Connected TCP stream to {address}, sending SHandShake...");
        // 1. Send Handshake
        bot.send_packet(&SHandShake {
            protocol_version: VarInt(CURRENT_MC_VERSION.protocol_version()),
            server_address: address.ip().to_string().into_boxed_str(),
            server_port: address.port(),
            next_state: ConnectionState::Login,
        })
        .await?;

        debug!("[BOT] Sent SHandShake, sending SLoginStart (name={name})...");
        // 2. Send Login Start
        bot.send_packet(&SLoginStart {
            name: name.to_string().into_boxed_str(),
            uuid: bot.uuid,
        })
        .await?;

        // 3. Start packet processing loop in background
        let bot_clone = bot.clone();
        tokio::spawn(async move {
            if let Err(error) = bot_clone.read_packets().await {
                *bot_clone.failure.lock().await = Some(format!("{error:#}"));
            }
            trace!("[BOT] Reader loop ended, marking closed=true");
            bot_clone.closed.store(true, Ordering::Release);
        });

        Ok(bot)
    }

    async fn read_packets(&self) -> anyhow::Result<()> {
        let mut connection_state = ConnectionState::Login;
        while !self.closed.load(Ordering::Acquire) {
            let raw = self
                .network_reader
                .lock()
                .await
                .get_raw_packet()
                .await
                .with_context(|| format!("Reading bot packet in {connection_state:?}"))?;
            *self.last_received.lock().await = Some((connection_state, raw.id));
            trace!(state = ?connection_state, packet_id = raw.id, bytes = raw.payload.len(), "Bot received packet");
            let mut payload = &raw.payload[..];
            match connection_state {
                ConnectionState::Login => {
                    if raw.id == CSetCompression::to_id(CURRENT_MC_VERSION) {
                        let packet = CSetCompression::read(&mut payload, &CURRENT_MC_VERSION)
                            .context("Reading login compression settings")?;
                        let threshold = usize::try_from(packet.threshold.0)
                            .context("Negative login compression threshold")?;
                        self.network_reader.lock().await.set_compression(threshold);
                        self.network_writer
                            .lock()
                            .await
                            .set_compression((threshold, 6));
                    } else if raw.id == CLoginSuccess::to_id(CURRENT_MC_VERSION) {
                        self.send_packet(&SLoginAcknowledged).await?;
                        connection_state = ConnectionState::Config;
                    } else if raw.id == CLoginDisconnect::to_id(CURRENT_MC_VERSION) {
                        let packet = CLoginDisconnect::read(&mut payload, &CURRENT_MC_VERSION)
                            .context("Reading login disconnect")?;
                        anyhow::bail!("Server rejected bot login: {}", packet.json_reason);
                    }
                }
                ConnectionState::Config => {
                    if raw.id == CKnownPacks::to_id(CURRENT_MC_VERSION) {
                        // Select packs only in response to the server's offer.
                        self.send_packet(&SKnownPacks {
                            known_packs: Vec::new(),
                        })
                        .await?;
                    } else if raw.id == CConfigPing::to_id(CURRENT_MC_VERSION) {
                        let packet = CConfigPing::read(&mut payload, &CURRENT_MC_VERSION)
                            .context("Reading configuration ping")?;
                        self.send_packet(&SConfigPong { id: packet.id }).await?;
                    } else if raw.id == CFinishConfig::to_id(CURRENT_MC_VERSION) {
                        self.send_packet(&SAcknowledgeFinishConfig).await?;
                        connection_state = ConnectionState::Play;
                    } else if raw.id == CConfigDisconnect::to_id(CURRENT_MC_VERSION) {
                        anyhow::bail!("Server disconnected bot during configuration");
                    }
                }
                ConnectionState::Play => {
                    if raw.id == CKeepAlive::to_id(CURRENT_MC_VERSION) {
                        let packet = CKeepAlive::read(&mut payload, &CURRENT_MC_VERSION)
                            .context("Reading play keep-alive")?;
                        self.send_packet(&SKeepAlive {
                            keep_alive_id: packet.keep_alive_id,
                        })
                        .await?;
                    } else if raw.id == CPlayerPosition::to_id(CURRENT_MC_VERSION) {
                        let packet = CPlayerPosition::read(&mut payload, &CURRENT_MC_VERSION)
                            .context("Reading initial player teleport")?;
                        self.send_packet(&SConfirmTeleport {
                            teleport_id: packet.teleport_id,
                            position: packet.position,
                            yaw: packet.yaw,
                            pitch: packet.pitch,
                        })
                        .await?;
                        self.send_packet(&SPlayerLoaded).await?;
                        self.in_play.store(true, Ordering::Release);
                    }
                }
                _ => {}
            }
        }
        Ok(())
    }

    pub async fn wait_for_play(&self, timeout: Duration) -> anyhow::Result<()> {
        let start = std::time::Instant::now();
        while start.elapsed() < timeout {
            if self.in_play.load(Ordering::Acquire) {
                return Ok(());
            }
            if self.closed.load(Ordering::Acquire) {
                if let Some(error) = self.failure.lock().await.as_ref() {
                    anyhow::bail!("Bot connection failed before Play: {error}");
                }
                anyhow::bail!("Bot connection closed before entering Play state");
            }
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
        let last_received = *self.last_received.lock().await;
        anyhow::bail!(
            "Timed out waiting for bot to enter Play state; last received: {last_received:?}"
        );
    }

    pub async fn send_command(&self, command: &str) -> anyhow::Result<()> {
        self.send_packet(&SChatCommand {
            command: command.trim_start_matches('/'),
        })
        .await
    }

    pub async fn swing_arm(&self) -> anyhow::Result<()> {
        self.send_packet(&SSwingArm { hand: VarInt(0) }).await
    }

    pub async fn move_to(&self, x: f64, y: f64, z: f64) -> anyhow::Result<()> {
        self.send_packet(&SPlayerPosition {
            position: Vector3::new(x, y, z),
            collision: 1, // on ground
        })
        .await
    }

    pub async fn disconnect(&self) {
        self.closed.store(true, Ordering::Release);
        let mut writer = self.network_writer.lock().await;
        let _ = writer.flush().await;
    }
}
