/**
 * Just a trivial example method that contains a jump target
 * and uses no local variables aside from the argument. 
 */
public class Example {
    
    public static int f1(int i) {
        try {
            return i;
        } catch (Exception e) {
            return i;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("it worked: " + f1(123));
    }

}
