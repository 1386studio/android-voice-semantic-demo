package com.example.voicesemantic;
import java.util.List;

/** Conservative suggestion, NOT ASR grammar, NOT an executed command. */
final class ShortCommandHints {
    static String suggest(String transcript,List<String> phrases) {
        if(transcript==null||phrases==null)return "";
        String input=clean(transcript);
        if(input.length()<5 || input.length()>70)return "";
        int best=Integer.MAX_VALUE,second=Integer.MAX_VALUE;
        String candidate="";
        for(String item:phrases){
            String p=clean(item);
            if(p.length()<5 || Math.abs(p.length()-input.length())>2)continue;
            int dist=distance(input,p,3);
            if(dist<best){second=best;best=dist;candidate=item;}
            else if(dist<second)second=dist;
        }
        if(best==0)return candidate;
        // Non-exact suggestions require a unique, long enough candidate.
        if(best<=1 && second>=best+2 && input.length()>=7)return candidate;
        return "";
    }
    private static String clean(String x){return x.replaceAll("[\\s，。；！!?？、,.]","").trim();}
    private static int distance(String a,String b,int max){
        int n=a.length(),m=b.length();
        int[] prev=new int[m+1],cur=new int[m+1];
        for(int j=0;j<=m;j++)prev[j]=j;
        for(int i=1;i<=n;i++){
            cur[0]=i;int rowMin=cur[0];
            for(int j=1;j<=m;j++){
                cur[j]=Math.min(Math.min(prev[j]+1,cur[j-1]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
                rowMin=Math.min(rowMin,cur[j]);
            }
            if(rowMin>max)return max+1;
            int[] swap=prev;prev=cur;cur=swap;
        }
        return prev[m];
    }
}
