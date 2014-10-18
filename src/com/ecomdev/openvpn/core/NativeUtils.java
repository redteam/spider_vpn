package com.ecomdev.openvpn.core;

import java.security.InvalidKeyException;

public class NativeUtils {
	public static native byte[] rsasign(byte[] input,int pkey) throws InvalidKeyException;
	static native void jniclose(int fdint);

	static {
        System.loadLibrary("stlport_shared");
		System.loadLibrary("opvpnutil");
	}
}
