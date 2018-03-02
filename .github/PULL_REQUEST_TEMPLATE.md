Please do not create a Pull Request without first creating an ISSUE.
Any change needs to be discussed before proceeding.
Failure to do so may result in the rejection of the Pull Request.

Explain the **details** for making this change: What existing problem does the Pull Request solve? Why is this feature beneficial?

**On adding new feautes/endpoints**

No more than 1 endpoint should be coded within a Pull Request. 
This alone requires enough code to review and adding more than 1 WILL result in your Pull Request either being ignored or rejected outright.

**On adding Mock and Integ Tests**

At _least_ 2 mock tests and 2 integ tests are required prior to merging. 
Each pair should should test what the success and failure of added change looks like.
More complicated additions will of course require more tests.

**On CI testing (currently using travis)**

Code will not be reviewed until CI passes.
Current CI does NOT exercise `integ` tests and so each Pull Request will have to be run manually by one of the maintainers to confirm it works as expected: please be patient.

**On automtatic closing of ISSUES**

Put `closes #XXXX` in your comment to auto-close the issue that your PR fixes (if such).
