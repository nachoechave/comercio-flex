import { initialPickupBranchId, validPickupBranch } from './pickup-branch-selection';

const one = [{ id: 'branch-1', name: 'Principal', address: 'Calle 1' }];
const two = [...one, { id: 'branch-2', name: 'Centro', address: 'Calle 2' }];

describe('pickup branch selection', () => {
  it('auto-selects only when there is one branch', () => {
    expect(initialPickupBranchId(one)).toBe('branch-1');
    expect(initialPickupBranchId(two)).toBeNull();
  });

  it('requires a branch returned by the backend', () => {
    expect(validPickupBranch(two, 'branch-2')).toBe(true);
    expect(validPickupBranch(two, 'other')).toBe(false);
    expect(validPickupBranch(two, null)).toBe(false);
  });
});
