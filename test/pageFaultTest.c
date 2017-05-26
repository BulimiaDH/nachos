#include "syscall.h"
#include "stdio.h"

/**
 * A test program that generates at LEAST n page faults
 */

int main()
{
    int n = 10;
    int pageSize = 1024;
    int bufSize = n * pageSize / sizeof(int);

    int buf [bufSize];

    int i = 0;
    for (; i < bufSize; i++)
        buf[i] = 0;

    return 0;
}

