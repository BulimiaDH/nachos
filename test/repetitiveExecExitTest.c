#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main()
{
    printf("Executing repetitveExecExitTest ...\n");
    printf("This test whether our project 2 implementation recycle resource cleanly.\n");
    printf("It tests it by repetitvely exec hello.coff and join it.\n");
    printf("Ideally, the test should run indefinitely until the user suspend the execution\n");

    char * argv [1];
    argv[0] = "hello.coff";

    while (true) {
        int childpid = exec("hello.coff", 1, argv);
        int* status;
        int joinRes = join(childpid, status);
        if (joinRes != 1) {
            printf("Join error! status: %d\n", joinRes);
            break;
        }
    }
    printf("repetitiveExecExitTest unexpected ends.\n");

   return 0;
}

