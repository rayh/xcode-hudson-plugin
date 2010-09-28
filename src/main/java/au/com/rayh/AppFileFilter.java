/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.com.rayh;

import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;

/**
 *
 * @author ray
 */
public class AppFileFilter implements FileFilter,Serializable {
    public boolean accept(File pathname) {
        return pathname.isDirectory() && pathname.getName().endsWith(".app");
    }
}
