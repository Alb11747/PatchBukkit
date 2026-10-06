/// Keeps synchronous JNI callbacks on the JVM worker's OS thread while allowing
/// native callbacks to wait for Tokio work without remaining in an async context.
pub(super) fn dispatch_sync<T>(dispatch: impl FnOnce() -> T) -> T {
    tokio::task::block_in_place(dispatch)
}

#[cfg(test)]
mod tests {
    use super::dispatch_sync;

    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn synchronous_dispatch_can_block_without_changing_thread() {
        let worker_thread = std::thread::current().id();
        let (sender, receiver) = tokio::sync::oneshot::channel();
        tokio::spawn(async move {
            sender.send(42).expect("receiver must remain available");
        });
        let value = dispatch_sync(|| {
            assert_eq!(worker_thread, std::thread::current().id());
            receiver
                .blocking_recv()
                .expect("native callback must receive its result")
        });
        assert_eq!(42, value);
        assert_eq!(worker_thread, std::thread::current().id());
    }
}
