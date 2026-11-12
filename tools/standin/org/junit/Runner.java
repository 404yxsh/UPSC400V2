package org.junit;
import java.lang.reflect.Method;
public class Runner {
    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;
        for (String cn : args) {
            Class<?> c = Class.forName(cn);
            for (Method m : c.getMethods()) {
                if (m.getAnnotation(Test.class) == null) continue;
                Object o = c.getDeclaredConstructor().newInstance();
                try { m.invoke(o); pass++; System.out.println("PASS " + c.getSimpleName() + "." + m.getName()); }
                catch (java.lang.reflect.InvocationTargetException e) {
                    fail++; System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + " -> " + e.getCause());
                }
            }
        }
        System.out.println("passed=" + pass + " failed=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
