package kg.delletenebre.serialmanager.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;

import android.util.Log;

public class FileUtils {
    private FileUtils() {
    }

    static public File FindFile(File dir, final String name, final String othername, int maxRecursion) {
        if (maxRecursion == 0) return null;
        File[] fs = dir.listFiles();
        if (fs==null) return null;
        for (File f : fs) {
            if (f.isDirectory()) {
                File result = FindFile(f, name, othername, maxRecursion - 1);
                if (result != null) return result;
            }
            if (f.getName().equals(name)) {
                Log.d("find",dir.getAbsolutePath());
                if (othername==null)
                    return f;
                if (new File(f.getAbsolutePath()+othername).isFile()) {
                    return f;
                }
            }
        }
        return null;
    }

    static public File GetFile(File dir, final String name) {
        if (dir == null) return null;
        File[] fs = dir.listFiles(new FilenameFilter() {
            public boolean accept(File arg0, String arg1) {
                return arg1.equals(name);
            }
        });
        if (fs != null && fs.length > 0) return fs[0];
        return null;
    }

    static public String ReadFile(File dir, final String name) {
        File fs = FileUtils.GetFile(dir, name);
        if (fs != null) {
            StringBuffer sb = new StringBuffer();
            try {
                BufferedReader bis = new BufferedReader(new InputStreamReader(new FileInputStream(fs)));
                String line;
                while ((line = bis.readLine()) != null) {
                    sb.append(line);
                }
            } catch (FileNotFoundException e) {
                return null;
            } catch (IOException e) {
                return sb.toString();
            }
            return sb.toString();
        } else {
            return null;
        }
    }
}
