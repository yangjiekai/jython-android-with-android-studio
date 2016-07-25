package org.jython_on_android;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.Properties;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.python.util.PythonInterpreter;

public class JythonOnAndroidRunScript extends Activity {
    public final static int ID = R.raw.main;
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        copyResourcesToLocal();

        Properties props = new Properties();
        props.setProperty("python.path", this.getFilesDir().getAbsolutePath());
        PythonInterpreter.initialize(System.getProperties(), props, new String[] {""});
        PythonInterpreter interp = new PythonInterpreter();
        String main_file =  this.getFilesDir().getAbsolutePath() + "/main.jython";
        Log.d("Jython-For-Android", "main_file = " + main_file);
        interp.execfile(main_file);
    }

    public void copyResourcesToLocal() {
      String name, sFileName;
      InputStream content;
      R.raw a = new R.raw();
      java.lang.reflect.Field[] t = R.raw.class.getFields();
      Resources resources = getResources();
      for (int i = 0; i < t.length; i++) {
        try {
          name = resources.getText(t[i].getInt(a)).toString();
          Log.d("Jython-For-Android", "Copying " + i + " - " + name);
          sFileName = name.substring(name.lastIndexOf("res/raw/") + 8, name .length());
          Log.d("Jython-For-Android", "Copying " + i + " - " + sFileName);
          content = getResources().openRawResource(t[i].getInt(a));

          // Copies script to internal memory only if changes were made
          sFileName = this.getFilesDir()
            .getAbsolutePath()
            + "/" + sFileName;
          if (needsToBeUpdated(sFileName, content)) {
            Log.d("Jython-For-Android", "Copying from stream " + sFileName);
            content.reset();
            this.copyFromStream(sFileName, content);
          }
          this.chmod(new File(sFileName), 0755);
        } catch (Exception e) {  
          // TODO Auto-generated catch block
          e.printStackTrace();
        }   
      }
    }

    public boolean needsToBeUpdated(String filename, InputStream content) {
      File script = new File(filename);
      FileInputStream fin;
      Log.d("Jython-For-Android", "Checking if " + filename + " exists");

      if (!script.exists()) {
        Log.d("Jython-For-Android", "not found");
        return true;
      }

      Log.d("Jython-For-Android", "Comparing file with content");
      try {
        fin = new FileInputStream(filename);
        int c;
        while ((c = fin.read()) != -1) {
          if (c != content.read()) {
            Log.d("Jython-For-Android", "Something changed replacing");
            return true;
          }
        }
      } catch (Exception e) {
        Log.d("Jython-For-Android", "Something failed during comparing");
        Log.e("Jython-For-Android", e.toString());
        return true;
      }
      Log.d("Jython-For-Android", "No need to update " + filename);
      return false;
    }
    
    public File copyFromStream(String name, InputStream input) {
      if (name == null || name.length() == 0) {
        Log.e("Jython-For-Android", "No script name specified.");
        return null;
      }
      File file = new File(name);
      if (!makeDirectories(file.getParentFile(), 0755)) {
        return null;
      }
      try {
        OutputStream output = new FileOutputStream(file);
        copy(input, output);
      } catch (Exception e) {
        Log.e("Jython-For-Android", e.toString());
        return null;
      }
      return file;
    }
    private static final int BUFFER_SIZE = 1024 * 8;         
    public int copy(InputStream input, OutputStream output) throws Exception, IOException {
      byte[] buffer = new byte[BUFFER_SIZE];

      BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
      BufferedOutputStream out = new BufferedOutputStream(output, BUFFER_SIZE);
      int count = 0, n = 0;
      try {
        while ((n = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
          out.write(buffer, 0, n);
          count += n;
        }
        out.flush();
      } finally {
        try {
          out.close();
        } catch (IOException e) {
          Log.e(e.getMessage(), e.toString());
        }
        try {
          in.close();
        } catch (IOException e) {
          Log.e(e.getMessage(), e.toString());
        }
      }
      return count;
    }

    public static int chmod(File path, int mode) throws Exception {
      Class<?> fileUtils = Class.forName("android.os.FileUtils");
      Method setPermissions =
        fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
      return (Integer) setPermissions.invoke(null, path.getAbsolutePath(), mode, -1, -1);
    }

    public boolean makeDirectories(File directory, int mode) {
      File parent = directory;
      while (parent.getParentFile() != null && !parent.exists()) {
        parent = parent.getParentFile();
      }
      if (!directory.exists()) {
        Log.v("Jython-For-Android", "Creating directory: " + directory.getName());
        if (!directory.mkdirs()) {
          Log.e("Jython-For-Android", "Failed to create directory.");
          return false;
        }
      }
      try {
        this.recursiveChmod(parent, mode);
      } catch (Exception e) {
        Log.e("Jython-For-Android", e.toString());
        return false;
      }
      return true;
    }

    public boolean recursiveChmod(File root, int mode) throws Exception {
      boolean success = this.chmod(root, mode) == 0;
      for (File path : root.listFiles()) {
        if (path.isDirectory()) {
          success = this.recursiveChmod(path, mode);
        }
        success &= (this.chmod(path, mode) == 0);
      }
      return success;
    }
}

