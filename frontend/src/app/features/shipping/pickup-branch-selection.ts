import { PickupBranch } from './pickup-branch';

export function initialPickupBranchId(branches: PickupBranch[]): string | null {
  return branches.length === 1 ? branches[0].id : null;
}

export function validPickupBranch(branches: PickupBranch[], branchId: string | null): boolean {
  return !!branchId && branches.some((branch) => branch.id === branchId);
}
