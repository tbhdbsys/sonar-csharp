﻿using System;
using System.Collections.Generic;
using System.Runtime.Versioning;

namespace Tests.Diagnostics
{
    public class Resource : IDisposable
    {
        public void Dispose()
        {
        }
        public int DoSomething()
        {
            return 1;
        }
        public int DoSomethingElse()
        {
            return 5;
        }
    }

    public class DeadStores
    {
        void calculateRate(int a, int b)
        {
            b = doSomething(); // Noncompliant {{Remove this useless assignment to local variable 'b'.}}
//            ^^^^^^^^^^^^^^^

            int i, j;
            i = a + 12;
            i += i + 2; // Noncompliant
            i = 5;
            j = i;
            i
                = doSomething(); // Noncompliant; retrieved value overwritten in for loop
            for (i = 0; i < j + 10; i++)
            {
                //  ...
            }

            if ((i = doSomething()) == 5 ||
                (i = doSomethingElse()) == 5)
            {
                i += 5; // Noncompliant
            }

            var resource = new Resource(); // Noncompliant; retrieved value not used
            using (resource = new Resource())
            {
                resource.DoSomething();
            }

            var x
                = 10; // Noncompliant
            var y =
                x = 11; // Noncompliant
            Console.WriteLine(y);

            int k = 12; // Noncompliant
            X(out k);   // Compliant, not reporting on out parameters
        }
        void X(out int i) { i = 10; }

        void calculateRate(int a, int b) // We don't report on methods with try-catch
        {
            var x = 0;
            x = 1;
            try
            {
                x = 11;
                x = 12;
                Console.Write(x);
                x = 13;
            }
            catch (Exception)
            {
                x = 21;
                x = 22;
                Console.Write(x);
                x = 23;
            }
            x = 31;
        }

        void calculateRate2(int a, int b)
        {
            int i;

            i = doSomething();
            i += a + b;
            storeI(i);

            for (i = 0; i < 10; i++)
            {
                //  ...
            }
        }

        int pow(int a, int b)
        {
            if (b == 0)
            {
                return 0;
            }
            int x = a;
            for (int i = 1; i < b; i++)
            {
                x = x * a;  //Not detected yet, we are in a loop, Dead store because the last return statement should return x instead of returning a
            }
            return a;
        }
        public void Switch()
        {
            const int c = 5; // Compliant
            var b = 5;
            switch (b)
            {
                case 6:
                    b = 5; // Noncompliant
                    break;
                case 7:
                    b = 56; // Noncompliant
                    break;
                case c:
                    break;
            }

            b = 7;
            Console.Write(b);
            b += 7; // Noncompliant
        }

        public int Switch1(int x)
        {
            var b = 0; // Noncompliant
            switch (x)
            {
                case 6:
                    b = 5;
                    break;
                case 7:
                    b = 56;
                    break;
                default:
                    b = 0;
                    break;
            }

            return b;
        }

        public int Switch2(int x)
        {
            var b = 0; // Compliant
            switch (x)
            {
                case 6:
                    b = 5;
                    break;
                case 7:
                    b = 56;
                    break;
            }

            return b;
        }

        private int MyProp
        {
            get
            {
                var i = 10;
                Console.WriteLine(i);
                i++; // Noncompliant
                i = 12;
                ++i; // Noncompliant
                i = 12;
                var a = ++i;
                return a;
            }
        }

        private int MyProp2
        {
            get
            {
                var i = 10; // Noncompliant
                if (nameof(((i))) == "i")
                {
                    i = 11;
                }
                else
                {
                    i = 12;
                }
                Console.WriteLine(i);
            }
        }

        public List<int> Method(int i)
        {
            var l = new List<int>();

            Func<List<int>> func = () =>
            {
                return (l = new List<int>(new [] {i}));
            };

            var x = l; // Noncompliant
            x = null;  // Noncompliant

            return func();
        }

        public List<int> Method2(int i)
        {
            var l = new List<int>(); // Compliant, not reporting on captured variables

            return (() =>
            {
                var k = 10; // Noncompliant
                k = 12; // Noncompliant
                return (l = new List<int>(new[] { i })); // l captured here
            })();
        }

        public List<int> Method3(int i)
        {
            bool f = false;
            if (true || (f = false))
            {
                if (f) { }
            }
        }

        public List<int> Method4(int i)
        {
            bool f;
            f = true;
            if (true || (f = false))
            {
                if (f) { }
            }
        }

        public List<int> Method5(out int i, ref int j)
        {
            i = 10; // Compliant, out parameter

            j = 11;
            if (j == 11)
            {
                j = 12; // Compliant, ref parameter
            }
        }
        public void Method5Call1(out int i)
        {
            int x = 10;
            Method5(out i, x);
        }

        public void Method5Call2()
        {
            int x;
            Method5Call1(out x); // Compliant, reporting on this can be considered false positive, although it's not.
        }

        public List<int> Method6()
        {
            var i = 10;
            Action a = () => { Console.WriteLine(i); };
            i = 11; // Not reporting on captured local variables
            a();
        }

        public List<int> Method7_Foreach()
        {
            foreach (var item in new int[0])
            {
                Console.WriteLine(item);
            }

            foreach (var
                item // A new value is assigned here, which is not used. But we are not reporting on it.
                in new int[0])
            {
            }
        }

        public void Unused()
        {
            var x = 5; // Compliant, S1481 already reports on it.

            var y = 5; // Noncompliant
            y = 6; // Noncompliant
        }

        private void SimpleAssignment(bool b1, bool b2)
        {
            var x = false;  // Noncompliant
            (x) = b1 && b2; // Noncompliant
            x = b1 && b2;   // Noncompliant
        }

        private class NameOfTest
        {
            private int MyProp2
            {
                get
                {
                    var i = 10; // Compliant
                    if (nameof(((i))) == "i")
                    {
                        i = 11;
                    }
                    else
                    {
                        i = 12;
                    }
                    Console.WriteLine(i);
                }
            }

            private static string nameof(int i)
            {
                return "some text";
            }
        }
    }
}
