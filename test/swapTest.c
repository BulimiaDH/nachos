#include "syscall.h"
#include "stdio.h"

int main()
{
    int numPage = 3;
    int bufSize = numPage * 1024 / sizeof(int);

    int buf [bufSize];

    int i = 0;
    for (; i < bufSize; i++)
        buf[i] = i;

    return -100;
}

