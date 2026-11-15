import com.example.voicesemantic.CerMetrics;
public final class CerMetricsTest {
    public static void main(String[] args) {
        if(CerMetrics.cer("查询三号终端电量","查询三号终端电量")!=0)throw new AssertionError();
        if(CerMetrics.cer("三号终端电量","三号终端")!=2.0/6)throw new AssertionError();
        System.out.println("CerMetricsTest PASSED");
    }
}
