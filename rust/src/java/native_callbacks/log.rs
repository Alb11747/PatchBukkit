use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::log::{LogLevel, SendLogRequest},
};

pub fn ffi_native_bridge_send_log_impl(request: SendLogRequest) -> Option<()> {
    let logger = &request.logger_name;
    let msg = &request.message;

    // Filter internal JDK / system loggers
    if logger.starts_with("jdk.") || logger.starts_with("sun.") || logger.starts_with("javax.") {
        return Some(());
    }

    let min_level = CALLBACK_CONTEXT
        .get()
        .map(|ctx| ctx.config.diagnostics.log_level.as_str())
        .unwrap_or("info");

    let debug_bridge = CALLBACK_CONTEXT
        .get()
        .map(|ctx| ctx.config.diagnostics.debug_bridge)
        .unwrap_or(false);

    let allowed = debug_bridge
        || match min_level {
            "error" => matches!(request.level(), LogLevel::Severe),
            "warn" | "warning" => matches!(request.level(), LogLevel::Severe | LogLevel::Warning),
            "info" => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info
            ),
            "debug" => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info | LogLevel::Config
            ),
            "trace" | "all" => true,
            _ => matches!(
                request.level(),
                LogLevel::Severe | LogLevel::Warning | LogLevel::Info
            ),
        };

    if !allowed {
        return Some(());
    }

    match request.level() {
        LogLevel::Severe => tracing::error!(logger = %logger, "{}", msg),
        LogLevel::Warning => tracing::warn!(logger = %logger, "{}", msg),
        LogLevel::Info => tracing::info!(logger = %logger, "{}", msg),
        LogLevel::Config => tracing::debug!(logger = %logger, "{}", msg),
        LogLevel::Fine => tracing::trace!(logger = %logger, "{}", msg),
        LogLevel::Finer => tracing::trace!(logger = %logger, "{}", msg),
        LogLevel::Finest => tracing::trace!(logger = %logger, "{}", msg),
        LogLevel::Off => {}
    }

    Some(())
}
