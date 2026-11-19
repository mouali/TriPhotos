import org.junit.Assert;
import org.junit.Test;

public class CalcTest {
    Calc calc = new Calc();
    private int a = 20;
    private int b = 10;

    @Test
    public void sum_test(){
        int c = calc.sum(a, b);
        Assert.assertEquals(c, 30);
    }
    @Test
    public void minus_test(){
        int c = calc.minus(a, b);
        Assert.assertEquals(c, 10);
    }
}
