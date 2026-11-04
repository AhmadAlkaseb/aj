package com.aj.productview;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProjectionRulesTest { @Test void enforcesBusinessBoundaries(){assertTrue(ProjectionRules.obsolete(10,9));assertTrue(ProjectionRules.obsolete(10,10));assertFalse(ProjectionRules.obsolete(10,11));assertThrows(IllegalArgumentException.class,()->ProjectionRules.obsolete(10,-1));assertThrows(IllegalArgumentException.class,()->ProjectionRules.page(0,101));} }
