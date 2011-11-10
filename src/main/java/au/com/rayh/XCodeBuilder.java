/*
 * The MIT License
 *
 * Copyright (c) 2011 Ray Yamamoto Hilton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package au.com.rayh;
import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ray Hilton
 */
public class XCodeBuilder extends Builder {
    public final Boolean buildIpa;
    public final Boolean cleanBeforeBuild;
    public final String configuration;
    public final String cfBundleShortVersionStringPattern;
    public final String target;
    public final String sdk;
    public final String xcodeProjectPath;
    public final String xcodeProjectFile;
    public final String embeddedProfileFile;
    public final String cfBundleVersionPattern;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public XCodeBuilder(Boolean buildIpa, Boolean cleanBeforeBuild, String configuration, String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String embeddedProfileFile, String cfBundleVersionPattern, String cfBundleShortVersionStringPattern) {
        this.buildIpa = buildIpa;
        this.sdk = sdk;
        this.target = target;
        this.cleanBeforeBuild = cleanBeforeBuild;
        this.cfBundleShortVersionStringPattern = cfBundleShortVersionStringPattern;
        this.configuration = configuration;
        this.xcodeProjectPath = xcodeProjectPath;
        this.xcodeProjectFile = xcodeProjectFile;
        this.embeddedProfileFile = embeddedProfileFile;
        this.cfBundleVersionPattern = cfBundleVersionPattern;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        EnvVars envs = build.getEnvironment(listener);
        FilePath projectRoot = build.getProject().getWorkspace();

        // check that the configured tools exist
        if(!new FilePath(projectRoot.getChannel(), getDescriptor().xcodebuildPath()).exists()) {
            listener.fatalError("Cannot find xcodebuild with the configured path {0}", getDescriptor().xcodebuildPath());
        }
        if(!new FilePath(projectRoot.getChannel(), getDescriptor().agvtoolPath()).exists()) {
            listener.fatalError("Cannot find agvtool with the configured path {0}", getDescriptor().agvtoolPath());
        }

        // Set the working directory
        if(!StringUtils.isEmpty(xcodeProjectPath)) {
            projectRoot = projectRoot.child(xcodeProjectPath);
        }
        listener.getLogger().println("Working directory is " + projectRoot);
        FilePath buildDirectory = projectRoot.child("build").child(configuration + "-iphoneos");

        // XCode Version
        int returnCode = launcher.launch().envs(envs).cmds(getDescriptor().xcodebuildPath(), "-version").stdout(listener).pwd(projectRoot).join();
        if(returnCode>0){
            listener.fatalError("Check your XCode installation. Jenkins cannot retrieve its version.");
            return false; // We fail the build if XCode isn't deployed
        }

        listener.getLogger().println("Fetching marketing version number (CFBundleShortVersionString) from project.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String cfBundleShortVersionString="";
        returnCode = launcher.launch().envs(envs).cmds("agvtool", "mvers", "-terse1").stdout(output).pwd(projectRoot).join();
        // only use this version number if we found it
        if(returnCode==0)
            cfBundleShortVersionString = output.toString().trim();
        if(cfBundleShortVersionString.isEmpty())
            listener.getLogger().println("No marketing version found (CFBundleShortVersionString)");
        else
            listener.getLogger().println("Found marketing version (CFBundleShortVersionString)"+cfBundleShortVersionString);

        String cfBundleVersion="";
        returnCode = launcher.launch().envs(envs).cmds("agvtool", "vers", "-terse").stdout(output).pwd(projectRoot).join();
        // only use this version number if we found it
        if(returnCode==0)
            cfBundleVersion = output.toString().trim();
        if(cfBundleVersion.isEmpty())
            listener.getLogger().println("No marketing version found (CFBundleVersion)");
        else
            listener.getLogger().println("Found marketing version (CFBundleVersion)"+cfBundleShortVersionString);

        listener.getLogger().println("Marketing version (CFBundleShortVersionString) found in project configuration :" + cfBundleShortVersionString);
        listener.getLogger().println("Technical version (CFBundleVersion) found in project configuration :"+ cfBundleVersion);

        // Update the Marketing version (CFBundleShortVersionString)
        if( false == StringUtils.isEmpty(cfBundleShortVersionStringPattern) ) {
            try {
                // If not empty we use the Token Expansion to replace it
                // https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
                cfBundleShortVersionString = TokenMacro.expand(build, listener, cfBundleShortVersionStringPattern);
                listener.getLogger().println("Updating marketing version (CFBundleShortVersionString) to " + cfBundleShortVersionString);
                returnCode = launcher.launch().envs(envs).cmds(getDescriptor().agvtoolPath(), "new-marketing-version", cfBundleShortVersionString).stdout(listener).pwd(projectRoot).join();
                if(returnCode>0) {
                    listener.fatalError("Could not set CFBundleShortVersionString to " + cfBundleShortVersionString);
                    return false;
                }
            } catch (MacroEvaluationException e) {
                listener.fatalError("Failure while expanding macros for CFBundleShortVersionString. Error : "+e.getMessage());
                return false;
            }
        }

        if( ! StringUtils.isEmpty(cfBundleVersionPattern) ) {
            try {
                // If not empty we use the Token Expansion to replace it
                // https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
                String technicalVersion = TokenMacro.expand(build, listener, cfBundleVersionPattern);
                output = new ByteArrayOutputStream();
                // Read the current Marketing Version in the project
                cfBundleVersion = cfBundleShortVersionString.isEmpty() ? technicalVersion : cfBundleShortVersionString + "_" + technicalVersion;
                listener.getLogger().println("CFBundleShortVersionString is " + cfBundleShortVersionString + " so new CFBundleVersion will be updated to " + cfBundleVersion);
                returnCode = launcher.launch().envs(envs).cmds(getDescriptor().agvtoolPath(), "new-version", "-all", cfBundleVersion ).stdout(listener).pwd(projectRoot).join();
                if(returnCode>0) {
                    listener.fatalError("Could not set the CFBundleVersion to " + cfBundleVersion);
                    return false;
                }
            } catch (MacroEvaluationException e) {
                listener.fatalError("Failure while expanding macros for CFBundleVersion. Error : "+e.getMessage());
                // Fails the build
                return false;
            }

        }

        listener.getLogger().println("Marketing version (CFBundleShortVersionString) used by Jenkins to produce the IPA :" + cfBundleShortVersionString);
        listener.getLogger().println("Technical version (CFBundleVersion) used by Jenkins to produce the IPA :"+ cfBundleVersion);

        // Clean build directories
        if(cleanBeforeBuild) {
            listener.getLogger().println("Cleaning build directory (" + projectRoot.child("build") + ")");
            buildDirectory.deleteRecursive();
        }

        // remove test-reports and *.ipa
        listener.getLogger().println("Cleaning up test-reports");
        projectRoot.child("test-reports").deleteRecursive();

        // Build
        StringBuilder xcodeReport = new StringBuilder("Going to invoke xcodebuild: ");
        XCodeBuildOutputParser reportGenerator = new XCodeBuildOutputParser(projectRoot, listener);
        List<String> commandLine = Lists.newArrayList(getDescriptor().xcodebuildPath());
        if(StringUtils.isEmpty(target)) {
            commandLine.add("-alltargets");
            xcodeReport.append("target: ALL");
        } else {
            commandLine.add("-target");
            commandLine.add(target);
            xcodeReport.append("target: ").append(target);
        }

        if(!StringUtils.isEmpty(sdk)) {
            commandLine.add("-sdk");
            commandLine.add(sdk);
            xcodeReport.append(", sdk: ").append(sdk);
        } else {
            xcodeReport.append(", sdk: DEFAULT");
        }

        if(!StringUtils.isEmpty(xcodeProjectFile)) {
            commandLine.add("-project");
            commandLine.add(xcodeProjectFile);
            xcodeReport.append(", project: ").append(xcodeProjectFile);
        } else {
            xcodeReport.append(", project: DEFAULT");
        }

        commandLine.add("-configuration");
        commandLine.add(configuration);
        xcodeReport.append(", configuration: ").append(configuration);

        if (cleanBeforeBuild) {
            commandLine.add("clean");
            xcodeReport.append(", clean: YES");
        } else {
            xcodeReport.append(", clean: NO");
        }
        commandLine.add("build");

        listener.getLogger().println(xcodeReport.toString());
        returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(reportGenerator.getOutputStream()).pwd(projectRoot).join();
        if(reportGenerator.getExitCode()!=0) return false;
        if(returnCode>0) return false;


        // Package IPA
        if(buildIpa) {
            listener.getLogger().println("Cleaning up previously generate .ipa files");
            for(FilePath path : buildDirectory.list("*.ipa")) {
                path.delete();
            }

            listener.getLogger().println("Packaging IPA");
            List<FilePath> apps = buildDirectory.list(new AppFileFilter());

            for(FilePath app : apps) {
                String version = "";
                if (cfBundleShortVersionString.isEmpty() && cfBundleVersion.isEmpty())
                    version = ""+build.getNumber();
                else if (cfBundleVersion.isEmpty())
                    version = cfBundleShortVersionString;
                else
                    version = cfBundleVersion;

                String baseName = app.getBaseName().replaceAll(" ","_") + "-" +
                        configuration.replaceAll(" ","_") + (version.isEmpty() ? "" : "-"+version);

                FilePath ipaLocation = buildDirectory.child(baseName + ".ipa");

                FilePath payload = buildDirectory.child("Payload");
                payload.deleteRecursive();
                payload.mkdirs();


                listener.getLogger().println("Packaging " + app.getBaseName() + ".app => " + ipaLocation);
                List<String> packageCommandLine = new ArrayList<String>();
                packageCommandLine.add("/usr/bin/xcrun");
                packageCommandLine.add("-sdk");

                if(!StringUtils.isEmpty(sdk)) {
                    packageCommandLine.add(sdk);
                } else {
                    packageCommandLine.add("iphoneos");
                }
                packageCommandLine.addAll(Lists.newArrayList("PackageApplication", "-v", app.toString(), "-o", ipaLocation.toString()));
                if(!StringUtils.isEmpty(embeddedProfileFile)) {
                    packageCommandLine.add("--embed");
                    packageCommandLine.add(embeddedProfileFile);
                }

                returnCode = launcher.launch().envs(envs).stdout(listener).pwd(projectRoot).cmds(packageCommandLine).join();
                if(returnCode>0) {
                    listener.getLogger().println("Failed to build " + ipaLocation.getName());
                    continue;
                }

                // also zip up the symbols, if present
                returnCode = launcher.launch().envs(envs).stdout(listener).pwd(buildDirectory).cmds("zip", "-r", "-T", "-y", baseName + "-dSYM.zip", app.getBaseName() + ".app.dSYM").join();
                if(returnCode>0) {
                    listener.getLogger().println("Failed to zip *.dSYM into " + baseName + "-dSYM.zip");
                    continue;
                }

                //listener.getLogger().println("Copying to " + app.getBaseName() + ".ipa");
                //ipaLocation.copyTo(buildDirectory.child(app.getBaseName() + ".ipa"));


                payload.deleteRecursive();
            }
        }

        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String xcodebuildPath = "/usr/bin/xcodebuild";
        private String agvtoolPath = "/usr/bin/agvtool";

        public FormValidation doCheckConfiguration(@QueryParameter String value) throws IOException, ServletException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please specify a configuration");
            } else {
                // TODO: scan project file for specified configuration
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckXcodebuildPath(@QueryParameter String value) throws IOException, ServletException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please specify the path to the xcodebuild executable (usually /usr/bin/xcodebuild)");
            } else {
                // TODO: check that the file exists
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAgvtoolPath(@QueryParameter String value) throws IOException, ServletException {
            if(StringUtils.isEmpty(value))
                return FormValidation.error("Please specify the path to the agvtool executable (usually /usr/bin/agvtool)");
            else {
                // TODO: check that the file exists
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return "XCode";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
//            updateBuildNumber = formData.getBoolean("updateBuildNumber");
//            buildIpa = formData.getBoolean("buildIpa");
//            cleanBeforeBuild = formData.getBoolean("cleanBeforeBuild");
//            configuration = formData.getString("configuration");
            xcodebuildPath = formData.getString("xcodebuildPath");
            agvtoolPath = formData.getString("agvtoolPath");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        public String agvtoolPath() {
            return agvtoolPath;
        }

        public String xcodebuildPath() {
            return xcodebuildPath;
        }

    }
}

