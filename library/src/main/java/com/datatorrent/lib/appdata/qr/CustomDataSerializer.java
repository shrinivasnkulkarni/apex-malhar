/*
 *  Copyright (c) 2012-2015 Malhar, Inc.
 *  All Rights Reserved.
 */

package com.datatorrent.lib.appdata.qr;

/**
 *
 * @author Timothy Farkas: tim@datatorrent.com
 */
public interface CustomDataSerializer
{
  public abstract String serialize(Result result);
}