package ch.deletescape.lawnchair.util;

public abstract class Provider {
    public abstract Object get();

    public static Provider of(Object obj) {
        return new ProviderImpl(obj);
    }

    static final class ProviderImpl extends Provider {
        final Object value;

        ProviderImpl(Object obj) {
            this.value = obj;
        }

        @Override
        public Object get() {
            return this.value;
        }
    }
}