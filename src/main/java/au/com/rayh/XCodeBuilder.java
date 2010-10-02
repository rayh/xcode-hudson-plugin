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
import java.io.ByteArrayOutputStream;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/**
 * @author Ray Hilton
 */
public class XcodeBuilder extends Builder {
    private Boolean buildIpa;
    private Boolean cleanBeforeBuild;
    private Boolean updateBuildNumber;
    private String configuration = "Release";

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public XcodeBuilder(Boolean buildIpa, Boolean cleanBeforeBuild, Boolean updateBuildNumber, String configuration) {
        this.buildIpa = buildIpa;
        this.cleanBeforeBuild = cleanBeforeBuild;
        this.updateBuildNumber = updateBuildNumber;
        this.configuration = configuration;
    }

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

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        EnvVars envs = build.getEnvironment(listener);

        // XCode Version
        int returnCode = launcher.launch().envs(envs).cmds(getDescriptor().xcodebuildPath(), "-version").stdout(listener).pwd(build.getProject().getWorkspace()).join();
        if(returnCode>0) return false;


        // Set build number
        if(updateBuildNumber) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            returnCode = launcher.launch().envs(envs).cmds("agvtool", "mvers", "-terse1").stdout(output).pwd(build.getProject().getWorkspace()).join();
            if(returnCode>0) return false;
            String marketingVersionNumber = output.toString().trim();
            String newVersion = marketingVersionNumber + "." + build.getNumber();
            listener.getLogger().println("CFBundlerShortVersionString is " + marketingVersionNumber + " so new CFBundleVersion will be " + newVersion);

            returnCode = launcher.launch().envs(envs).cmds(getDescriptor().agvtoolPath(), "new-version", "-all", newVersion ).stdout(listener).pwd(build.getProject().getWorkspace()).join();
            if(returnCode>0) return false;
        }


        // Build
        XcodeBuildOutputParser reportGenerator = new XcodeBuildOutputParser(build.getProject().getWorkspace(), listener);
        List<String> commandLine = Lists.newArrayList(getDescriptor().xcodebuildPath(), "-alltargets", "-configuration", configuration);
        if (cleanBeforeBuild) {
            commandLine.add("clean");
        }
        commandLine.add("build");
        returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(reportGenerator.getOutputStream()).pwd(build.getProject().getWorkspace()).join();
        if(reportGenerator.getExitCode()!=0) return false;
        if(returnCode>0) return false;


        // Package IPA
        if(buildIpa) {
            FilePath buildDir = build.getProject().getWorkspace().child("build").child(configuration + "-iphoneos");
            List<FilePath> apps = buildDir.list(new AppFileFilter());

            for(FilePath app : apps) {
                FilePath ipaLocation = buildDir.child(app.getBaseName() + ".ipa");
                ipaLocation.delete();

                FilePath payload = buildDir.child("Payload");
                payload.deleteRecursive();
                payload.mkdirs();

                app.copyRecursiveTo(payload.child(app.getName()));
                payload.zip(ipaLocation.write());

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

