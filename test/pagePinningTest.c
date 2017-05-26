#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

/**
 * Precondition: Set the physical page number to be 4.
 *
 * Test if our implementation can afford 5 readWriteTest.coff
 */
int main()
{
   int numPhysPage = 4;
   int numChildren = numPhysPage + 1;

   int cpids [numChildren];
   int i = 0;
   for (; i < numChildren; i++) {
       printf("Execing child #%d ...\n",i);
        cpids[i] = exec("hello.coff", 0, 0);
   }
  int* status;
  i = 0;
  for (; i < numChildren; i++) {
       int joinRes = join(cpids[i], status);
       if (joinRes != 1) {
           printf("Joining child #%d error! status: %d\n", i+1, joinRes);
       }
   }

   printf("End of pagePinningTest\n");

   return -1000;
}

