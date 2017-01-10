import static org.assertj.core.api.Assertions.assertThat;

public class VMWareImplPlatformTests extends VMWarePlatformTests{

    @Override
    public IVMWare getVmWareImpl() {
        return new VMWareImpl();
    }
    
    @Override
    public TestResourceFactory getTestResourceFactory(){
        return new TestResourceFactory(System.getProperty("user.name"));
    }

    @Override
    public String getvCenterUrl() {
        String vCenterUrl = System.getenv("VCENTER_URL");
        
        assertThat(vCenterUrl).as("The environment variable 'VCENTER_URL' is not set.").isNotNull();
        
        return vCenterUrl + "/sdk/vimservice";
    }
}