package com.aj.productview;
public final class ProjectionRules {
 private ProjectionRules() {}
 public static boolean obsolete(long current,long incoming) {if(incoming<0) throw new IllegalArgumentException("Negative version");return incoming<=current;}
 public static void page(int offset,int limit) {if(offset<0 || offset>100000 || limit<1 || limit>100) throw new IllegalArgumentException("Invalid page bounds");}
}
