﻿using System;
using System.Collections.Generic;

namespace Tests.Diagnostics
{

    class UnaryPrefixOperatorRepeated
    {
        static void NonComp(bool b  )
        {
            int i = 1;

            int k = i; // Fixed
            int m = + +i;  // Compliant

            bool b = false;
            bool c = !b; // Fixed

            NonComp(!b); // Fixed
        }

        static void Comp()
        {
            int i = 1;

            int j = -i;
            j = -(-i); //Compliant, not a typo
            int k = i;
            int m = i;

            bool b = false;
            bool c = !b;
        }
    }
}
