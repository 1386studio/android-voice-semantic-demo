package com.example.voicesemantic;

/** Unicode-codepoint edit distance (CER). Trim whitespace/punctuation for command-level comparison. */
public final class CerMetrics {
    private CerMetrics(){}
    public static double cer(String reference,String candidate) {
        int[] a=normalize(reference).codePoints().toArray();
        int[] b=normalize(candidate).codePoints().toArray();
        if(a.length==0)return b.length==0?0:1;
        int[] prev=new int[b.length+1],curr=new int[b.length+1];
        for(int j=0;j<=b.length;j++)prev[j]=j;
        for(int i=1;i<=a.length;i++){
            curr[0]=i;
            for(int j=1;j<=b.length;j++)
                curr[j]=Math.min(Math.min(curr[j-1]+1,prev[j]+1),prev[j-1]+(a[i-1]==b[j-1]?0:1));
            int[] tmp=prev;prev=curr;curr=tmp;
        }
        return (double)prev[b.length]/a.length;
    }
    private static String normalize(String s) {
        if(s==null)return "";
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{Punct}，。！？、；：]","");
    }
}
