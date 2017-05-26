#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main()
{
//   exec("hello.coff",0,0);

   char * argv [4];
   argv[0] = "runWith4arg.coff";
   argv[1] = "argB";
   argv[2] = "argC";
   argv[3] = "D";

   exec("runWith4arg.coff", 4, argv);

   return 0;
}

