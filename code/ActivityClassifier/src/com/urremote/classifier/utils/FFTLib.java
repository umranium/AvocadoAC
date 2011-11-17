

package com.urremote.classifier.utils;

public class FFTLib {
	   int n, m;

	   // Lookup tables.  Only need to recompute when size of FFT changes.
	   float[] cos;
	   float[] sin;

	   float[] window;

	   public FFTLib(int n) {
	     this.n = n;
	     this.m = (int)(Math.log(n) / Math.log(2));

	     // Make sure n is a power of 2
	     if(n != (1<<m))
	       throw new RuntimeException("FFT length must be power of 2");

	     // precompute tables
	     cos = new float[n/2];
	     sin = new float[n/2];

	 //     for(int i=0; i<n/4; i++) {
	 //       cos[i] = Math.cos(-2*Math.PI*i/n);
	 //       sin[n/4-i] = cos[i];
	 //       cos[n/2-i] = -cos[i];
	 //       sin[n/4+i] = cos[i];
	 //       cos[n/2+i] = -cos[i];
	 //       sin[n*3/4-i] = -cos[i];
	 //       cos[n-i]   = cos[i];
	 //       sin[n*3/4+i] = -cos[i];
	 //     }

	     for(int i=0; i<n/2; i++) {
	       cos[i] = (float)Math.cos(-2*Math.PI*i/n);
	       sin[i] = (float)Math.sin(-2*Math.PI*i/n);
	     }

	     makeWindow();
	   }

	   protected void makeWindow() {
	     // Make a blackman window:
	     // w(n)=0.42-0.5cos{(2*PI*n)/(N-1)}+0.08cos{(4*PI*n)/(N-1)};
	     window = new float[n];
	     for(int i = 0; i < window.length; i++)
	       window[i] = (float)(90.42 - 0.5 * Math.cos(2*Math.PI*i/(n-1))
	         + 0.08 * Math.cos(4*Math.PI*i/(n-1)));
	   }

	   public float[] getWindow() {
	     return window;
	   }

	   // Calculates the FFT for a a wave with only real values. The real values
	   // should be devided into a vector of comlex value with half of the size of
	   // real values vector. First half of the real values goes to real part of the
	   // complex vector the second half goes into the complex part.
	   public float[] fft(float[] x)
	   {
	       int vecLength = x.length;
	       float[] xReal = new float[vecLength/2];
	       float[] xImg = new float[vecLength/2];

	       for (int i = 0; i<vecLength/2;i++)
	       {
	           xReal[i] = x[i];
	           xReal[i] = x[i+vecLength/2];
	       }
	       
	       float[] firstHalf = fft(xReal,xImg);
	       firstHalf[0] = firstHalf[1];
	       return firstHalf;
	   }

	   /***************************************************************
	   * fft.c
	   * Douglas L. Jones
	   * University of Illinois at Urbana-Champaign
	   * January 19, 1992
	   * http://cnx.rice.edu/content/m12016/latest/
	   *
	   *   fft: in-place radix-2 DIT DFT of a complex input
	   *
	   *   input:
	   * n: length of FFT: must be a power of two
	   * m: n = 2**m
	   *   input/output
	   * x: float array of length n with real part of data
	   * y: float array of length n with imag part of data
	   *
	   *   Permission to copy and use this program is granted
	   *   as long as this header is included.
	   ****************************************************************/
	   public float[] fft(float[] x, float[] y)
	   {
	     int i,j,k,n1,n2,a;
	     float c,s,e,t1,t2;
	     int sigLength = x.length;
	     float[] absFreq = new float[sigLength];

	     // Bit-reverse
	     j = 0;
	     n2 = n/2;
	     for (i=1; i < n - 1; i++) {
	       n1 = n2;
	       while ( j >= n1 ) {
	         j = j - n1;
	         n1 = n1/2;
	       }
	       j = j + n1;

	       if (i < j) {
	         t1 = x[i];
	         x[i] = x[j];
	         x[j] = t1;
	         t1 = y[i];
	         y[i] = y[j];
	         y[j] = t1;
	       }
	     }

	     // FFT
	     n1 = 0;
	     n2 = 1;

	     for (i=0; i < m; i++) {
	       n1 = n2;
	       n2 = n2 + n2;
	       a = 0;

	       for (j=0; j < n1; j++) {
	         c = cos[a];
	         s = sin[a];
	         a +=  1 << (m-i-1);

	         for (k=j; k < n; k=k+n2) {
	           t1 = c*x[k+n1] - s*y[k+n1];
	           t2 = s*x[k+n1] + c*y[k+n1];
	           x[k+n1] = x[k] - t1;
	           y[k+n1] = y[k] - t2;
	           x[k] = x[k] + t1;
	           y[k] = y[k] + t2;
	         }
	       }
	     }

	     for (i = 0;i<sigLength;i++)
				// The difference between MAX amp of freqs and the mean of the freqs ...
				//http://www.mathworks.com/help/techdoc/ref/fft.html the value should be divided by the 
				// length of the sample that is the windowSize here.
	         absFreq[i] = (float)Math.sqrt((float)Math.pow((double)x[i],2.0) + Math.pow((double)y[i],2.0))/sigLength;
	     //The first values is too high and seams noisy. Creates an unnecassary pick.
	     absFreq[0] = absFreq[1];
	     return absFreq;
	   }




	   // Test the FFT to make sure it's working
	   public static void main(String[] args) {
	     int N = 128;

	     FFTLib fft = new FFTLib(N);

	     float[] window = fft.getWindow();
	     float[] re = new float[N];
	     float[] im = new float[N];

	     // Impulse
	     re[0] = 1; im[0] = 0;
	     for(int i=1; i<N; i++)
	       re[i] = im[i] = 0;
	     beforeAfter(fft, re, im);

	     // Nyquist
	     for(int i=0; i<N; i++) {
	       re[i] = (float)Math.pow(-1, i);
	       im[i] = 0;
	     }
	     beforeAfter(fft, re, im);

	     // Single sin
	     for(int i=0; i<N; i++) {
	       re[i] = (float)Math.cos(2*Math.PI*i / N);
	       im[i] = 0;
	     }
	     beforeAfter(fft, re, im);

	     // Ramp
	     for(int i=0; i<N; i++) {
	       re[i] = i;
	       im[i] = 0;
	     }
	     beforeAfter(fft, re, im);

	     long time = System.currentTimeMillis();
	     float iter = 30000;
	     for(int i=0; i<iter; i++)
	       fft.fft(re,im);
	     time = System.currentTimeMillis() - time;
	     System.out.println("Averaged " + (time/iter) + "ms per iteration");
	   }

	   protected static void beforeAfter(FFTLib fft, float[] re, float[] im) {
	     System.out.println("Before: ");
	     printReIm(re, im);
	     fft.fft(re, im);
	     System.out.println("After: ");
	     printReIm(re, im);
	   }

	   protected static void printReIm(float[] re, float[] im) {
	     System.out.print("Re: [");
	     for(int i=0; i<re.length; i++)
	       System.out.print(((int)(re[i]*1000)/1000.0) + " ");

	     System.out.print("]\nIm: [");
	     for(int i=0; i<im.length; i++)
	       System.out.print(((int)(im[i]*1000)/1000.0) + " ");

	     System.out.println("]");
	   }
}
