/// <reference path="../../../typings/tsd.d.ts" />
/// <reference path="../../../typings/vsts-task-lib/vsts-task-lib.d.ts" />

import * as tl from "vsts-task-lib/task";
import * as util from "util";
import * as path from "path";

export class VmOperations {
    // tl.getInput will exit the task if required input is null or empty, we were not able to have
    // a platform test for this behavior as process is exiting if you don't stub
    public static getCmdCommonArgs(): string {
        var cmdArgs = "";
        var vCenterConnectionName: string = tl.getInput("vCenterConnection", true);
        var vCenterUrl: string = tl.getEndpointUrl(vCenterConnectionName, false) + "sdk/vimService";
        var endPointAuthCreds = tl.getEndpointAuthorization(vCenterConnectionName, false)["parameters"];
        var vCenterUserName: string = endPointAuthCreds["username"];
        var vCenterPassword: string = endPointAuthCreds["password"];
        var vmList: string = tl.getInput("vmList", true);
        var skipca: string = tl.getInput("skipca", false);
        var targetdc: string = tl.getInput("targetdc", true);
        this.validateVmListInput(vmList);

        cmdArgs += " -vCenterUrl \"" + vCenterUrl  + "\" -vCenterUserName \"" + vCenterUserName  + "\" -vCenterPassword \"" +
                 vCenterPassword + "\" -vmList \"" + vmList + "\" -targetdc \"" + targetdc + "\" -skipca " + skipca;
        tl.debug(util.format("common args: -vCenterUrl \"%s\" -vCenterUserName \"%s\" -vCenterPassword \"%s\" -vmList \"%s\" -targetlocaltion \"%s\" -skipca %s",
                vCenterUrl, vCenterUserName, "**********", vmList, targetdc, skipca));
        return cmdArgs;
    }

    public static getCmdArgsForAction(actionName: string): string {
        var cmdArgs = "";
        var timeout = "";
        switch (actionName) {
            case "Deploy Virtual Machines using Template":
                var template = tl.getInput("template", true);
                var folder = tl.getInput("targetFolder", false);
                var computeType = tl.getInput("computeType", true);
                var datastore = tl.getInput("datastore", true);
                var description = tl.getInput("description", false);
                var customizationspec = tl.getInput("customizationspec", false);
                timeout = tl.getInput("timeout", false);
                var computeName = null;
                switch (computeType) {
                    case "ESXi Host":
                        computeName = tl.getInput("hostname", true);
                        break;
                    case "Cluster":
                        computeName = tl.getInput("clustername", true);
                        break;
                    case "Resource Pool":
                        computeName = tl.getInput("resourcepoolname", true);
                        break;
                    default:
                        tl.error("Invalid compute type : " + computeType);
                        tl.exit(1);
                }
                cmdArgs += " -clonetemplate \"" + template  + "\" -computetype \"" + computeType + "\" -folder \"" + folder + "\" -computename \"" +
                        computeName + "\" -datastore \"" + datastore + "\" -customizationspec \"" + customizationspec + "\" -description \"" + description + "\" -timeout " + timeout;
                break;
            case "Take Snapshot of Virtual Machines":
                var snapshotName = tl.getInput("snapshotName", true);
                var snapshotVMMemory = tl.getInput("snapshotMemory", true);
                var quiesceGuestFileSystem = "false";
                var description: string = tl.getInput("description", false);
                timeout = tl.getInput("timeout", false);
                cmdArgs += " -snapshotOps create -snapshotName \"" + snapshotName  + "\" -snapshotVMMemory " + snapshotVMMemory + " -quiesceGuestFileSystem " +
                            quiesceGuestFileSystem + " -description \"" + description + "\" -timeout " + timeout;
                break;
            case "Revert Snapshot of Virtual Machines":
                var snapshotName  = tl.getInput("snapshotName", true);
                timeout = tl.getInput("timeout", false);
                cmdArgs += " -snapshotOps restore -snapshotName \"" + snapshotName  + "\" -timeout " + timeout;
                break;
            case "Delete Snapshot of Virtual Machines":
                var snapshotName  = tl.getInput("snapshotName", true);
                cmdArgs += " -snapshotOps delete -snapshotName \"" + snapshotName  + "\"";
                break;
            case "Delete Virtual Machines":
                cmdArgs += " -deletevm delete";
                break;
            case "Power On Virtual Machines":
                timeout = tl.getInput("timeout", false);
                cmdArgs += " -powerops poweron -timeout " + timeout;
                break;
            case "Shutdown Virtual Machines":
                timeout = tl.getInput("timeout", false);
                cmdArgs += " -powerops shutdown -timeout " + timeout;
                break;
            case "Power off Virtual Machines":
                cmdArgs += " -powerops poweroff";
                break;
            default:
                tl.error("Invalid action name : " + actionName);
                tl.exit(1);
        }
        tl.debug(util.format("action args: %s", cmdArgs));
        return cmdArgs;
    }

    public static runMain(): Q.Promise<void> {
        var actionName: string = tl.getInput("action", true);
        var commonArgs: string = this.getCmdCommonArgs();
        var cmdArgsForAction: string = this.getCmdArgsForAction(actionName);
        var cwd: string = path.resolve(__dirname);
        tl.debug("Setting current working directory to : " + cwd);
        tl.cd(cwd);
        var systemClassPath: string = tl.getVariable("CLASSPATH");
        var cmdArgs = "-classpath vmOpsTool-1.0.jar" + path.delimiter + systemClassPath + " VmOpsTool " + cmdArgsForAction + commonArgs;
        util.log("Invoking command to perform vm operations ...\n");
        return tl.exec("java", cmdArgs, <any> {failOnStdErr: true})
            .then((code) => {
                tl.debug("Exit code: " + code);
                tl.exit(code);
            })
            .fail( (err) => {
                tl.error("Failure reason : " + err);
                tl.exit(1);
            });
    }

    private static validateVmListInput(vmList: any): void {
        var vms = vmList.split(",");
        vms.forEach(vm => {
            if (!vm.trim()) {
                tl.error("Invalid input for vmList: vmName cannot be empty string.");
                tl.exit(1);
            }
        });
    }
}
