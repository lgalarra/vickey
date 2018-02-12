package vickey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import amie.keys.CSAKey;
import sakey.SAKey;

public class VICKEY {

	public static void main(String[] args) throws IOException, InterruptedException {
		// The KB is always the last argument
		String[] sakeyArgs = new String[] {args[args.length - 1], "1"};		
		// We have to capture the standard output into a stream
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
	    // IMPORTANT: Save the old System.out!
	    PrintStream old = System.out;
	    // Tell Java to use your special stream
	    System.setOut(ps);
	    // We do our stuff
		SAKey.main(sakeyArgs);
	    // Put things back
	    System.out.flush();	    
	    // Restore everything
	    System.setOut(old);
	    // Show what happened
	    String[] sakeyOutput = baos.toString().split("\n");
	    if (sakeyOutput.length == 2) {
		    int indexOfColon = sakeyOutput[0].indexOf(':');
		    String nonKeysRawText = sakeyOutput[0].substring(indexOfColon + 1).trim();
		    File nonKeysFile = storeSAKeyOutput(nonKeysRawText);
		    if (nonKeysFile != null) {
			    String[] augmentedArguments = new String[2 + args.length];
			    augmentedArguments[0] = "-nk";
			    augmentedArguments[1] = nonKeysFile.getAbsolutePath();
			    for (int i = 0; i < args.length; ++i) {
			    	augmentedArguments[i + 2] = args[i];
			    }
			    System.out.println("The non-keys has been stored in the file " + nonKeysFile.getCanonicalPath());
			    CSAKey.main(augmentedArguments);
		    }

	    }
	    
	}

	private static File storeSAKeyOutput(String nonKeysRawText) throws IOException {
		String[] nonKeys = nonKeysRawText.split("\\], \\[");
		if (nonKeys.length > 1) {
			nonKeys[0] = nonKeys[0].replace("[[", "");
			nonKeys[nonKeys.length - 1] = nonKeys[nonKeys.length - 1].replace("]]", "");
			File tmpFileObj = File.createTempFile("non-keys-", ".txt");
			try(PrintWriter writer = new PrintWriter(tmpFileObj)) {
				for (String nk : nonKeys) {
					writer.println(nk);
				}
			}
			return tmpFileObj;
		}
		
		return null;
	}

}
