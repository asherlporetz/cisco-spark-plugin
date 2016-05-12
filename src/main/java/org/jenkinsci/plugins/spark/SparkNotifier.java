package org.jenkinsci.plugins.spark;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.spark.client.SparkClient;
import org.jenkinsci.plugins.spark.token.SparkToken;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


public class SparkNotifier extends Notifier {

    public static final String DEFAULT_CONTENT_KEY = "${DEFAULT_CONTENT}";
    public static final String DEFAULT_CONTENT_VALUE = "${BUILD_STATUS}  ${JOB_NAME}:${BUILD_NUMBER}  ${JOB_URL}";

    private final boolean disable;
    private final String sparkRoomName;
    private final String publishContent;

    @DataBoundConstructor
    public SparkNotifier(boolean disable, String sparkRoomName, String publishContent) {
        this.disable = disable;
        this.sparkRoomName = sparkRoomName;
        this.publishContent = publishContent;
        System.out.println("save:");
        System.out.println(disable);
        System.out.println(sparkRoomName);
        System.out.println(publishContent);
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getPublishContent() {
        return publishContent;
    }

    public String getSparkRoomName() {
        return sparkRoomName;
    }

    public boolean isDisable() {
        return disable;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a
        // build.

        // This also shows how you can consult the global configuration of the
        // builder

        PrintStream logger = listener.getLogger();
        if (!disable) {
            logger.println("[Cisco Spark Plugin]================[start]=================");
            try {
                DescriptorImpl descriptor = getDescriptor();
                SparkRoom sparkRoom = descriptor.getSparkRoom(sparkRoomName);

                logger.println("[Cisco Spark Plugin][Expand content]Before Expand: " + publishContent);
                String publishContentAfterInitialExpand=publishContent;
                if(publishContent.contains(DEFAULT_CONTENT_KEY)){
                    publishContentAfterInitialExpand=publishContent.replace(DEFAULT_CONTENT_KEY, DEFAULT_CONTENT_VALUE);
                }
                String expandAll = TokenMacro.expandAll(build, listener, publishContentAfterInitialExpand, false, getPrivateMacros());
                logger.println("[Cisco Spark Plugin][Expand content]After Expand: " + expandAll);

                logger.println("[Cisco Spark Plugin][Publish Content][begin]use:" + sparkRoom);
                SparkClient.sent(sparkRoom, expandAll);
                logger.println("[Cisco Spark Plugin][Publish Content][end]");

                logger.println("[Cisco Spark Plugin]================[end][success]=================");
            } catch (Exception e) {
                logger.println("[Cisco Spark Plugin]" + e.getMessage());
                logger.println("[Cisco Spark Plugin]" + Arrays.toString(e.getStackTrace()));
                logger.println("[Cisco Spark Plugin]================[end][failure]=================");
            }

        } else {
            logger.println("[Cisco Spark Plugin]================[skiped]=================");
        }

        return true;
    }

    private static List<TokenMacro> getPrivateMacros() {
        List<TokenMacro> macros = new ArrayList<TokenMacro>();
        ClassLoader cl = Jenkins.getInstance().pluginManager.uberClassLoader;
        for (final IndexItem<SparkToken, TokenMacro> item : Index.load(SparkToken.class, TokenMacro.class, cl)) {
            try {
                macros.add(item.instance());
            } catch (Exception e) {
                // ignore errors loading tokens
            }
        }
        return macros;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor for {@link SparkNotifier}. Used as a singleton. The class is
     * marked as public so that it can be accessed from views.
     *
     * <p>
     * See
     * <tt>src/main/resources/hudson/plugins/spark/SparkNotifier/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information, simply store it in a
         * field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private final CopyOnWriteList<SparkRoom> sparkRooms = new CopyOnWriteList<SparkRoom>();

        public DescriptorImpl() {
            super(SparkNotifier.class);
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *            This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the
         *         browser.
         */
        /*
         * public FormValidation doCheckName(@QueryParameter String value)
         * throws IOException, ServletException { if (value.length() == 0)
         * return FormValidation.error("Please set a name"); if (value.length()
         * < 4) return FormValidation.warning("Isn't the name too short?");
         * return FormValidation.ok(); }
         */

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public SparkRoom[] getSparkRooms() {
            return sparkRooms.toArray(new SparkRoom[sparkRooms.size()]);
        }

        public SparkRoom getSparkRoom(String sparkRoomName) {
            for (SparkRoom sparkRoom : sparkRooms) {
                if (sparkRoom.getName().equalsIgnoreCase(sparkRoomName))
                    return sparkRoom;
            }

            throw new RuntimeException("no such key: " + sparkRoomName);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Cisco Spark Notification";
        }

        public FormValidation doNameCheck(@QueryParameter String name) throws IOException, ServletException {
             FormValidation basicVerify = returnVerify(name,"name");
            if(basicVerify.kind.equals(FormValidation.ok().kind)){
                 int total=0;
                 for (SparkRoom sparkRoom : sparkRooms) {
                     if(sparkRoom.getName().equalsIgnoreCase(name.trim())){
                         total++;
                     }
                 }
                 if(total>1){
                     return  FormValidation.error("duplicated name: "+name);
                 }
                 return FormValidation.ok();
             }else{
               return basicVerify;
            }
         }

        public FormValidation doTokenCheck(@QueryParameter String token) throws IOException, ServletException {
            return returnVerify(token,"Bearer token");
         }

        public FormValidation doSparkRoomIdCheck(@QueryParameter String sparkRoomId) throws IOException, ServletException {
            return returnVerify(sparkRoomId,"Spark Room Id");
        }

        private FormValidation returnVerify(String value, String message) {
            if (null == value||value.length() == 0)
                return FormValidation.error("please input "+message);

            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            sparkRooms.replaceBy(req.bindParametersToList(SparkRoom.class, "spark.room."));

            for (SparkRoom sparkRoom : sparkRooms) {
                System.out.println(sparkRoom);
            }
            save();
            return true;
        }

    }

}