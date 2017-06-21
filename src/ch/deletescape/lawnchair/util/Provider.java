package ch.deletescape.lawnchair.util;

public abstract class Provider {

    static final class C05091 extends Provider {
        final /* synthetic */ Object val$value;

        C05091(Object obj) {
            this.val$value = obj;
        }

        @Override
        public Object get() {
            return this.val$value;
        }
    }

    public abstract Object get();

    public static Provider of(Object obj) {
        return new C05091(obj);
    }
}