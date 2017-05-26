#include "syscall.h"
#include "stdio.h"

int main(int argc, char ** argv)
{
    printf("This is runWith4arg.coff speaking\n");

    int i;
    for (i = 0; i < argc; i++) {
        printf("arg: %s\n", argv[i]);
    }

    return 0;
}

