public class InMemoryVMWareImplPlatformTests extends VMWarePlatformTests {

    @Override
    public IVMWare getVmWareImpl() {
        return new InMemoryVMWareImpl();
    }
    
    @Override
    public TestResourceFactory getTestResourceFactory(){
        return new TestResourceFactory("inmemory");
    }

    @Override
    public String getvCenterUrl() {
        return "http://localhost:8080/sdk/vimservice";
    }
}
