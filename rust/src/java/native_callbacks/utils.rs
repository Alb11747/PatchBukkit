use super::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::common::Uuid as ProtoUuid;
use pumpkin::entity::player::Player;
use std::collections::HashMap;
use std::ffi::{CStr, c_char};
use std::sync::{Arc, RwLock};

static PLAYER_HANDLE_CACHE: RwLock<Option<HashMap<uuid::Uuid, Arc<Player>>>> = RwLock::new(None);

#[must_use]
pub fn get_string(str_ptr: *const c_char) -> String {
    unsafe { CStr::from_ptr(str_ptr).to_string_lossy().into_owned() }
}

pub fn cache_player(player: Arc<Player>) {
    let player_uuid = player.gameprofile.id;
    if let Ok(mut write_guard) = PLAYER_HANDLE_CACHE.write() {
        let cache = write_guard.get_or_insert_with(HashMap::new);
        cache.insert(player_uuid, player);
    }
}

pub fn with_player<F, R>(proto_uuid: Option<&ProtoUuid>, f: F) -> Option<R>
where
    F: FnOnce(Arc<Player>) -> R,
{
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &proto_uuid?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let cached_player = {
        if let Ok(read_guard) = PLAYER_HANDLE_CACHE.read()
            && let Some(ref cache) = *read_guard
        {
            cache.get(&player_uuid).cloned()
        } else {
            None
        }
    };

    // Callbacks can reenter cache_player, which needs the cache's write lock.
    if let Some(player) = cached_player {
        return Some(f(player));
    }

    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
    if let Ok(mut write_guard) = PLAYER_HANDLE_CACHE.write() {
        let cache = write_guard.get_or_insert_with(HashMap::new);
        cache.insert(player_uuid, player.clone());
    }

    Some(f(player))
}
