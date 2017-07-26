package javatools.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import telecom.util.collections.ByteStringCmp;
import javatools.datatypes.ByteString;

public class ByteStringSort {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		List<ByteString> list = new ArrayList<>();
		list.add(ByteString.of("<livesIn>"));
		list.add(ByteString.of("<wasBornIn>"));
		list.add(ByteString.of("<isCitizenOf>"));
		Collections.sort(list, new ByteStringCmp());
		System.out.println(list);
	}

}
