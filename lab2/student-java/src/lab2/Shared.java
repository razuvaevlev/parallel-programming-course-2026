package lab2;

public final class Shared<T> {
    private final String name;
    private T value;

    public Shared(String name, T value) { this.name = name; this.value = value; }

    public T get() {
        Scheduler.beforeSharedAccess();
        T result = value;
        Scheduler.record(name + " -> " + result);
        Scheduler.afterSharedAccess();
        return result;
    }

    public void set(T v) {
        Scheduler.beforeSharedAccess();
        value = v;
        Scheduler.record(name + " = " + v);
        Scheduler.afterSharedAccess();
    }

    public T getAcquire() {
        return get();
    }

    public void setRelease(T v) {
        set(v);
    }

    public T peek() { return value; }
}
