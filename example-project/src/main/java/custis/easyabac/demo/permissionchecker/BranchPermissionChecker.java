package custis.easyabac.demo.permissionchecker;

import custis.easyabac.api.PermitAwarePermissionChecker;
import custis.easyabac.demo.authz.abac.OrderAction;

public interface BranchPermissionChecker extends PermitAwarePermissionChecker<Branch, OrderAction> {

}
