package au.com.rayh;
import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/**
 * @author Ray Hilton
 */
public class XCodeBuilder extends Builder {
    private Boolean buildIpa;
    private Boolean cleanBeforeBuild;
    private Boolean updateBuildNumber;
    private String configuration;
    private String overrideMarketingNumber;
    //private String target;
    private String sdk;
    private String xcodeProjectPath;
    //private String xcodeProjectFile;
    private String embeddedProfileFile;
    private String versionNumberPattern;
    private String workspaceName;
    private String schemeName;
    private String buildAction;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public XCodeBuilder(Boolean buildIpa, Boolean cleanBeforeBuild, Boolean updateBuildNumber, String configuration, String sdk, String xcodeProjectPath, String embeddedProfileFile, String versionNumberPattern, String overrideMarketingNumber, String workspaceName, String schemeName, String buildAction) {
        this.buildIpa = buildIpa;
        this.sdk = sdk;
        //this.target = target;
        this.cleanBeforeBuild = cleanBeforeBuild;
        this.updateBuildNumber = updateBuildNumber;
        this.overrideMarketingNumber = overrideMarketingNumber;
        this.configuration = configuration;
        this.xcodeProjectPath = xcodeProjectPath;
        //this.xcodeProjectFile = xcodeProjectFile;
        this.embeddedProfileFile = embeddedProfileFile;
        this.versionNumberPattern = versionNumberPattern;
        this.workspaceName = workspaceName;
    	this.schemeName = schemeName;
    	this.buildAction = buildAction;
    }

    public String getVersionNumberPattern() {
        return versionNumberPattern;
    }
    public String getSdk() {
        return sdk;
    }

    //public String getTarget() {
    //    return target;
    //}

    public String getConfiguration() {
        return configuration;
    }

    public Boolean getBuildIpa() {
        return buildIpa;
    }

    public Boolean getCleanBeforeBuild() {
        return cleanBeforeBuild;
    }

    public Boolean getUpdateBuildNumber() {
        return updateBuildNumber;
    }

    public String getOverrideMarketingNumber() {
        return overrideMarketingNumber;
    }

    public String getXcodeProjectPath() {
        return xcodeProjectPath;
    }

    //public String getXcodeProjectFile() {
    //    return xcodeProjectFile;
    //}

    public String getEmbeddedProfileFile() {
        return embeddedProfileFile;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public String getBuildAction() {
        return buildAction;
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
        if(returnCode>0) return false;

        // Unlock keychain
//        if(!StringUtils.isEmpty(keychainPassword)) {
//            launcher.launch().envs(envs).cmds("security", "unlock-keychain", "-p", keychainPassword);
//        }

        // Set build number
        String artifactVersion = String.valueOf(build.getNumber());
        String versionNumber = artifactVersion;
        if(!StringUtils.isEmpty(getVersionNumberPattern())) {
             versionNumber = getVersionNumberPattern().replaceAll("\\{BUILD_NUMBER\\}", artifactVersion);
        }
        
        if(updateBuildNumber) {
            listener.getLogger().println("Updating version number (CFBundleVersion) to " + versionNumber);
            //ByteArrayOutputStream output = new ByteArrayOutputStream();
            //returnCode = launcher.launch().envs(envs).cmds("agvtool", "mvers", "-terse1").stdout(output).pwd(projectRoot).join();
            //if(returnCode>0) {

            //} else {
            //    String marketingVersionNumber = output.toString().trim();
            //    artifactVersion = marketingVersionNumber + "." + build.getNumber();
            //    listener.getLogger().println("CFBundleShortVersionString is " + marketingVersionNumber + " so new CFBundleVersion will be " + artifactVersion);
            //}

            returnCode = launcher.launch().envs(envs).cmds(getDescriptor().agvtoolPath(), "new-version", "-all", versionNumber ).stdout(listener).pwd(projectRoot).join();
            if(returnCode>0) {
                listener.fatalError("Could not set the CFBundleVersion to " + versionNumber);
            }
//        } else {
//            listener.getLogger().println("Fetching marketing version number (CFBundleShortVersionString)");
//            ByteArrayOutputStream output = new ByteArrayOutputStream();
//            returnCode = launcher.launch().envs(envs).cmds("agvtool", "vers", "-terse").stdout(output).pwd(projectRoot).join();
//
//            // only use this version number if we found it
//            if(returnCode==0)
//                artifactVersion = output.toString().trim();
        }
        
        if( false == StringUtils.isEmpty(overrideMarketingNumber) ) {
            listener.getLogger().println("Updating marketing version to " + overrideMarketingNumber);
            
            returnCode = launcher.launch().envs(envs).cmds(getDescriptor().agvtoolPath(), "new-marketing-version", overrideMarketingNumber ).stdout(listener).pwd(projectRoot).join();
            if(returnCode>0) {
                listener.fatalError("Could not set marketing version to " + overrideMarketingNumber);
            }
        }

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
        
        /*
        if(StringUtils.isEmpty(target)) {
            commandLine.add("-alltargets");
            xcodeReport.append("target: ALL");
        } else {
            commandLine.add("-target");
            commandLine.add(target);
            xcodeReport.append("target: ").append(target);
        }
        */
        
        commandLine.add("-workspace");
		commandLine.add(workspaceName);
		xcodeReport.append(", workspace: ").append(workspaceName);
        
        commandLine.add("-scheme");
		commandLine.add(schemeName);
		xcodeReport.append(", scheme: ").append(schemeName);
        
        if(!StringUtils.isEmpty(sdk)) {
            commandLine.add("-sdk");
            commandLine.add(sdk);
            xcodeReport.append(", sdk: ").append(sdk);
        } else {
            xcodeReport.append(", sdk: DEFAULT");
        }

		/*
        if(!StringUtils.isEmpty(xcodeProjectFile)) {
            commandLine.add("-project");
            commandLine.add(xcodeProjectFile);
            xcodeReport.append(", project: ").append(xcodeProjectFile);
        } else {
            xcodeReport.append(", project: DEFAULT");
        }*/

        commandLine.add("-configuration");
        commandLine.add(configuration);
        xcodeReport.append(", configuration: ").append(configuration);

//        if (cleanBeforeBuild) {
//            commandLine.add("clean");
//            xcodeReport.append(", clean: YES");
//        } else {
//            xcodeReport.append(", clean: NO");
//        }
        //commandLine.add("build");
        
		commandLine.add(buildAction);
		xcodeReport.append("buildAction: ").append(buildAction);
        
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
                String baseName = app.getBaseName() + "-" + configuration + "-" + build.getProject().getName() + "-" + versionNumber;
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
                returnCode = launcher.launch().envs(envs).stdout(listener).pwd(buildDirectory).cmds("zip", "-r", "-T", "-y", baseName + "-dSYM.zip", "*.dSYM").join();
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

