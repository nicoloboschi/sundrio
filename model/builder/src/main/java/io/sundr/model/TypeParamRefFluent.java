package io.sundr.model;

import java.lang.Boolean;
import java.lang.String;

/**
 * Generated
 */
public interface TypeParamRefFluent<A extends TypeParamRefFluent<A>> extends TypeRefFluent<A> {
  public String getName();

  public A withName(String name);

  public Boolean hasName();

  public int getDimensions();

  public A withDimensions(int dimensions);

  public Boolean hasDimensions();

}
