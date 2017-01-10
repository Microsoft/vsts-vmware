import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class VMWarePlatformTests {
    
    private final String vCenterUrl = getvCenterUrl();
    
    private final IVMWare vmWareImpl = getVmWareImpl();
    
    private final TestResourceFactory testResourceFactory = getTestResourceFactory();
    
    private final int operationTimeout = testResourceFactory.GetOperationTimeoutValue();

    public abstract IVMWare getVmWareImpl();
    
    public abstract TestResourceFactory getTestResourceFactory();

    public abstract String getvCenterUrl();
    
    @Rule public TestName testName = new TestName();
    
    // Common for all Operations
    // The following two scenarios needs to be validated during stress testing
    // restoreSnapshotShouldUseASessionTimeoutOfSixtyMinutes
    // restoreSnapshotShouldThrowIfWaitTimesOut
    // cloneVMFromTemplateWithInsufficientDiskSpaceShouldFail
    // cloneVMFromTemplateWithInsufficientComputeShouldFail

    // Clone VM from template action tests
    @Test
    public void cloneVMFromTemplateWithTargetComputeAsESXiHostShouldSucceed() throws Exception {
        
        String description = "Creating new VM from VM template on ESXi host";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        try
        {
            vmWareImpl.cloneVMFromTemplate(
                testResource.Template,
                testResource.NewVmName,
                testResource.ComputeType,
                testResource.ComputeName,
                testResource.Datastore,
                testResource.CustomizationSpec,
                description,
                operationTimeout,
                connData);

            assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(true);
        }
        finally
        {
            vmWareImpl.deleteVM(testResource.NewVmName, connData);
        }

        assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithTargetComputeAsClusterShouldSucceed() throws Exception {
        
        String description = "Creating new VM from VM template on cluster";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        try
        {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);

            assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(true);
        }
        finally
        {
            vmWareImpl.deleteVM(testResource.NewVmName, connData);
        }

        assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithTargetComputeAsResourcePoolShouldSucceed() throws Exception {
        
        String description = "Creating new VM from VM template on resource pool";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        try
        {
            vmWareImpl.cloneVMFromTemplate(
                testResource.Template,
                testResource.NewVmName,
                testResource.ComputeType,
                testResource.ComputeName,
                testResource.Datastore,
                testResource.CustomizationSpec,
                description,
                operationTimeout,
                connData);

            assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(true);
        }
        finally
        {
            vmWareImpl.deleteVM(testResource.NewVmName, connData);
        }

        assertThat(vmWareImpl.isVMExists(testResource.NewVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithInvalidTargetComputeShouldFail() throws Exception {
        
        String description = "Creating new VM with invalid template";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromNonExistingTemplateShouldFail() throws Exception {
        
        String description = "Creating new VM with invalid template";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetDatacenterShouldFail() throws Exception {
        
        String description = "Creating new VM from VM template on invalid data center";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetESXiShouldFail() throws Exception {
        
        String description = "Creating new VM from VM template on non existing ESXi host";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetCloudShouldFail() throws Exception {
        
        String description = "Creating new VM from VM template on non existing cluster";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetResourcePoolShouldFail() throws Exception {
        
        String description = "Creating new VM from ubuntuVM template on non existing resource pool";
        
        ConnectionData connData = GetTestConnectionData();
        TestResource testResource = GetTestResource();
        
        connData.setTargetDC(testResource.TargetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(
                    testResource.Template,
                    testResource.NewVmName,
                    testResource.ComputeType,
                    testResource.ComputeName,
                    testResource.Datastore,
                    testResource.CustomizationSpec,
                    description,
                    operationTimeout,
                    connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void connectShouldThrowForInvalidCredentials() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        
        Exception exp = null;
        try {
            vmWareImpl.connect(connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    public void connectShouldThrowWithoutSkipCACheck() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        
        Exception exp = null;
        try {
            vmWareImpl.connect(connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void connectShouldNotThrowOnSuccessfulAuthentication() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        
        vmWareImpl.connect(connData);
    }

    @Test
    public void createSnapshotWithSaveVMMemoryShouldSucceed() throws Exception {

        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.createSnapshot(vm.Name, vm.NewSnapshot, true, false, "Snapshot created during platform tests", operationTimeout, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.NewSnapshot);

        vmWareImpl.deleteSnapshot(vm.Name, vm.NewSnapshot, connData);
    }

    @Test
    public void createSnapshotWithQuiesceShouldSucceed() throws Exception {
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.createSnapshot(vm.Name, vm.NewSnapshot, false, true, "Snapshot created during platform tests", operationTimeout, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.NewSnapshot);

        vmWareImpl.deleteSnapshot(vm.Name, vm.NewSnapshot, connData);
    }

    @Test
    public void startAndStopOnAVmShouldNotThrow() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.powerOnVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, true, connData)).isEqualTo(true);
        vmWareImpl.shutdownVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, false, connData)).isEqualTo(false);
    }

    @Test
    public void startAndStopVmShouldSucceedForLinuxVM() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.powerOnVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, true, connData)).isEqualTo(true);
        vmWareImpl.powerOnVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, true, connData)).isEqualTo(true);

        vmWareImpl.shutdownVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, false, connData)).isEqualTo(false);
        vmWareImpl.shutdownVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, false, connData)).isEqualTo(false);
    }

    @Test
    public void powerOnAndPowerOffVmShouldSucceed() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.powerOnVM(vm.Name, operationTimeout, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, true, connData)).isEqualTo(true);

        vmWareImpl.powerOffVM(vm.Name, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vm.Name, false, connData)).isEqualTo(false);
    }

    // Common for restore/delete snapshot operations
    @Test
    public void restoreOrDeleteSnapshotShouldThrowIfSnapshotDoesNotExist() throws Exception {
        Exception exp = null;
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        try {
            vmWareImpl.restoreSnapshot(vm.Name, vm.Snapshot, operationTimeout, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();

        exp = null;
        try {
            vmWareImpl.deleteSnapshot(vm.Name, vm.Snapshot, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    // Common for Snapshot operations
    @Test
    public void restoreSnapshotShouldThrowIfVmDoesNotExist() throws Exception {
        Exception exp = null;
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        try {
            vmWareImpl.restoreSnapshot(vm.Name, vm.Snapshot, operationTimeout, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void restoreOrCreateOrDeleteSnapshotShouldSucceedIfVmIsShutdown() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.createSnapshot(vm.Name, vm.NewSnapshot, false, false, "Snapshot created during platform tests", operationTimeout, connData);
        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.NewSnapshot);

        vmWareImpl.restoreSnapshot(vm.Name, vm.Snapshot, operationTimeout, connData);
        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.Snapshot);

        vmWareImpl.deleteSnapshot(vm.Name, vm.NewSnapshot, connData);
        assertThat(vmWareImpl.isSnapshotExists(vm.Name, vm.NewSnapshot, connData)).isEqualTo(false);
    }

    @Test
    public void restoreSnapshotShouldThrowIfMultipleVmsWithSameNameExistInADC() throws Exception {
        
        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);
        Exception exp = null;

        try {
            vmWareImpl.restoreSnapshot(vm.Name, vm.Snapshot, operationTimeout, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void restoreOrCreateOrDeleteSnapshotShouldSucceedForVMsEvenIfCaseDoesNotMatch() throws Exception {

        ConnectionData connData = GetTestConnectionData();
        TestVirtualMachine vm = GetTestVirtualMachine();
        
        connData.setTargetDC(vm.TargetDC);

        vmWareImpl.createSnapshot(vm.Name, vm.NewSnapshot, false, false, "Snapshot created during platform tests", operationTimeout, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.NewSnapshot);

        vmWareImpl.restoreSnapshot(vm.Name, vm.Snapshot, operationTimeout, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vm.Name, connData)).isEqualTo(vm.Snapshot);

        vmWareImpl.deleteSnapshot(vm.Name, vm.NewSnapshot, connData);

        assertThat(vmWareImpl.isSnapshotExists(vm.Name, vm.NewSnapshot, connData)).isEqualTo(false);
    }

    private TestResource GetTestResource() throws Exception {
        return testResourceFactory.GetTestResource(testName.getMethodName());
    }

    private ConnectionData GetTestConnectionData() throws Exception {
        return testResourceFactory.GetConnectionData(vCenterUrl, testName.getMethodName());
    }

    private TestVirtualMachine GetTestVirtualMachine() throws Exception {
        return testResourceFactory.GetVirtualMachine(testName.getMethodName());
    }
}