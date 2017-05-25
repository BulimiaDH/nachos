#include "syscall.h"

int main (int argc, char *argv[])
{
    int arr[100000];
    int i;
    for (i = 0; i< 100000; i++){
        arr[i]= i;
        // printf(i);
    }

    return 0;
}
