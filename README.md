# PatchBukkit

A plugin for [PumpkinMC](https://pumpkinmc.org/) that adds support for [PaperMC](https://papermc.io/), [Spigot](https://www.spigotmc.org/), and [Bukkit](https://dev.bukkit.org/) plugins.

## Installation

1. **Requirement**: Install Java 25 or newer.
2. **Download**: Grab the library matching your operating system from the Releases page:
3. **Deploy**: Place the downloaded file into your PumpkinMC `plugins/` directory. (Run PumpkinMC once to generate this folder if it doesn't exist).
4. **Initialize**: Restart PumpkinMC. This creates `plugins/data/patchbukkit/` in your server root.
5. **Add Plugins**: Drop your .jar plugin files (Paper/Spigot/Bukkit) into the newly created `plugins/data/patchbukkit/patchbukkit-plugins/` folder and restart.

## Development

If you wish to contribute to PatchBukkit, follow the following steps:

1. Run `./build.sh` 
2. Copy the generated native library from `rust/target/debug/` (or `rust/target/release/`) to your PumpkinMC `plugins/` directory.
3. Start Pumpkin once to create the `plugins/data/patchbukkit/` directory in your server root, then place your `.jar` plugins in `plugins/data/patchbukkit/patchbukkit-plugins`.

There is also an [architecture guide](https://github.com/Pumpkin-MC/PatchBukkit/blob/master/ARCHITECTURE.md) available.

## Compatibility work in this fork

The item registry uses Pumpkin's generated item components instead of recursive Material lookups.
Synchronous Bukkit jobs run on the JVM worker; asynchronous jobs use their own executor, and
`isPrimaryThread()` reports the actual worker identity. The worker dispatches JNI outside Tokio's async
context so native callbacks can wait for server work without aborting the process.

These fixes require a matched Pumpkin build and native plugin ABI. Rebuild the Java jar before the
native library, and pin the server dependencies to the tested revision. Unit tests cover registry
publication, task execution and cancellation; enabling a plugin is still only a first runtime check.
The bridge's 50 ms scheduler heartbeat does not yet follow paused or altered server tick rates.

The fork pins the `pumpkin` dependency alias to the `pumpkin-core` library at server revision
`f028d0a1c8ffa0ba885287507388f08515b78b59` (native API 7). Server and bridge builds must also use the
same Rust toolchain, target and profile. The dev profile disables debuginfo to match that server
workspace. Existing native libraries built for another revision must be rebuilt.

The pinned core no longer exposes RCON command senders or `RemoteServerCommandEvent`. The bridge
rejects registration of that Bukkit event explicitly; player and console command mapping remains
available. Other Bukkit APIs may still be unimplemented, so verify each required plugin at runtime.

Baseline API 3 runtime checks verified Plan 5.8 build 3638 startup and a real player
join/disconnect recorded in SQLite. EssentialsX 2.22.0 reports its version, but player join and
`/sethome` fail in its permissions handler; its player commands require more compatibility work.
