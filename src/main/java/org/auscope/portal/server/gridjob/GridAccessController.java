package org.auscope.portal.server.gridjob;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.auscope.gridtools.GramJobControl;
import org.auscope.gridtools.GridJob;
import org.auscope.gridtools.MyProxyManager;
import org.auscope.gridtools.RegistryQueryClient;
import org.auscope.gridtools.SiteInfo;

import org.globus.exec.generated.JobDescriptionType;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.X509ExtensionSet;
import org.globus.gsi.bc.BouncyCastleCertProcessingFactory;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.globus.myproxy.MyProxyException;
import org.globus.wsrf.utils.FaultHelper;
import org.gridforum.jgss.ExtendedGSSManager;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;


/**
 * Following the MVC pattern, this class acts on events received by the UI,
 * and calls the methods in the Models (which actually do the work).
 *
 * @author Ryan Fraser
 * @author Terry Rankine
 * @author Darren Kidd
 */
public class GridAccessController {
    /** The handle to the <code>RegistryQueryClient</code> model. */
    private final RegistryQueryClient RQC = new RegistryQueryClient();

    /** The logger for this class */
    private Log logger = LogFactory.getLog(getClass());

    private String gridFtpServer = "";
    private String gridFtpStageInDir = "";
    private String gridFtpStageOutDir = "";

    // MyProxy settings
    private String myProxyServer = "myproxy.arcs.org.au";
    private int myProxyPort = 7512;

    /** Minimum lifetime for a proxy to be considered valid */
    private final int MIN_LIFETIME = 5*60;

    /** Current grid credential object */
    private GSSCredential credential = null;

    /**
     * Sets the server name of the GridFTP server to be used for file staging.
     *
     * @param gridFtpServer GridFTP server name
     */
    public void setLocalGridFtpServer(String gridFtpServer) {
        this.gridFtpServer = gridFtpServer;
    }

    /**
     * Returns the server name of the GridFTP server for file staging.
     *
     * @return The local GridFTP server name
     */
    public String getLocalGridFtpServer() {
        return gridFtpServer;
    }

    /**
     * Sets the directory on the local server to be used for stage-ins.
     *
     * @param gridFtpStageInDir stage-in directory
     */
    public void setLocalGridFtpStageInDir(String gridFtpStageInDir) {
        this.gridFtpStageInDir = gridFtpStageInDir;
    }

    /**
     * Returns the local stage-in directory.
     *
     * @return The stage-in directory
     */
    public String getLocalGridFtpStageInDir() {
        return gridFtpStageInDir;
    }

    /**
     * Sets the directory on the local server to be used for stage-outs.
     *
     * @param gridFtpStageOutDir stage-out directory
     */
    public void setLocalGridFtpStageOutDir(String gridFtpStageOutDir) {
        this.gridFtpStageOutDir = gridFtpStageOutDir;
    }

    /**
     * Returns the local stage-out directory.
     *
     * @return The stage-out directory
     */
    public String getLocalGridFtpStageOutDir() {
        return gridFtpStageOutDir;
    }

    /**
     * Submits a job to the Grid. The View packages the job properties into a
     * <code>GridJob</code> object, which is used to get the information needed
     * to submit the job properly.
     * <p>
     * Since not all information has been stored in the <code>GridJob</code>
     * object yet these details are retrieved first:
     * <ul>
     *   <li>the <em>executable name</em> of the code - this may not be the
     *       same as the name (i.e. 'List' is '<code>ls</code>'),</li>
     *   <li>the name of any modules that need to be loaded for the code
     *       to work,</li>
     *   <li>and the site's GridFTP server (where the data will be staged to,
     *       worked on, then staged from).</li>
     * </ul>
     * This method grabs this information, updates the <code>GridJob</code>
     * object, then uses <code>GramJobControl</code> to construct a job script
     * and submit the job.
     *
     * @param  job A <code>GridJob</code> object which contains all the
     *             information required to run a job
     * @return The submitted job's endpoint reference (EPR)
     */
    public String submitJob(GridJob job) {
        String siteAddr = RQC.getJobManagerAtSite(job.getSite());
        String moduleName = RQC.getModuleNameOfCodeAtSite(
                job.getSite(), job.getCode(), job.getVersion());
        String exeName = RQC.getExeNameOfCodeAtSite(
                job.getSite(), job.getCode(), job.getVersion());
        String gridFtpServer = RQC.getClusterGridFTPServerAtSite(
                job.getSite());

        job.setModules(new String[] { moduleName });
        job.setExeName(exeName);
        job.setSiteGridFTPServer(gridFtpServer);
        GramJobControl gjc = new GramJobControl(credential);
        String EPR = gjc.submitJob(job, siteAddr);

        if (EPR == null) {
            logger.error("Job did not submit (EPR was null).");
        } else {
            logger.info("Successfully submitted job. EPR = " + EPR);
        }
        return EPR;
    }

    /**
     * Kills a running grid job.
     *
     * @param reference The reference of the job to kill
     *
     * @return The status of the job after killing (a
     *         <code>StateEnumeration</code> string)
     */
    public String killJob(String reference) {
        GramJobControl ggj = new GramJobControl(credential);
        return ggj.killJob(reference);
    }

    /**
     * Checks the status of a job.
     *
     * @param reference The reference of the job to check the status of
     *
     * @return The status of the job (a <code>StateEnumeration</code> string)
     */
    public String retrieveJobStatus(String reference) {
        GramJobControl ggj = new GramJobControl(credential);
        return ggj.getJobStatus(reference);
    }

    /**
     * Starts a new job that transfers current files from given job
     * to the stage-out location.
     *
     * @param reference The reference of the job to get results from
     *
     * @return true if successful, false otherwise
     */
    public boolean retrieveJobResults(String reference) {
        GramJobControl ggj = new GramJobControl(credential);
        return (ggj.getJobResults(reference) != null);
    }

    /**
     * Retrieves a list of available sites on the grid.
     *
     * @return a list of available sites
     */
    public String[] retrieveAllSitesOnGrid() {
        String sites[] = RQC.getAllSitesOnGrid();

        // Order the sites alphabetically
        Arrays.sort(sites);

        return sites;
    }

    /**
     * Gets a list of codes available at a particular site.
     *
     * @param site The site to check
     *
     * @return a list of codes available
     */
    public String[] retrieveAllCodesAtSite(String site) {
        return RQC.getAllCodesAtSite(site);
    }

    /**
     * Gets the module name for a particular code.
     *
     * @param code The code to determine the module name for
     * @param site The site that code is being selected from
     *
     * @return The module name
     */
    public String retrieveModuleNameForCode(String code, String site, String version) {
        return RQC.getModuleNameOfCodeAtSite(site, code, version);
    }

    /**
     * Gets a list of sites that have a particular version of a code.
     *
     * @param code    The code to look for
     * @param version The version to look for
     * @return A list of sites that have the version of the code
     */
    public String[] retrieveSitesWithSoftwareAndVersion(String code,
            String version) {
        return RQC.getAllSitesWithAVersionOfACode(code, version);
    }

    /**
     * Returns subcluster with matching requirements. cpus, mem, and version
     * may be <code>null</code>.
     *
     * @param code    The code to use
     * @param version The version of the code required
     * @param cpus    The number of CPUs required
     * @param mem     The amount of memory required
     * @return The subcluster that satisfies the requirements
     */
    public String[] retrieveSubClusterWithSoftwareAndVersionWithMemAndCPUs(
            String code, String version, String cpus, String mem) {
        return RQC.getSubClusterWithSoftwareAndVersionWithMemAndCPUs(code, version, cpus, mem);
    }

    /**
     * Gets the queues that will honour the walltime and subcluster.
     *
     * @param currSubCluster The subcluster name
     * @param wallTime Wall time to look for
     * @return A list of queues
     */
    public String[] retrieveComputingElementForWalltimeAndSubcluster(
            String currSubCluster, String wallTime) {
        return RQC.getComputingElementForWalltimeAndSubcluster(
                currSubCluster, wallTime);
    }

    /**
     * Gets the storage element for given queue with available disk space.
     *
     * @param queue The computing element
     * @param diskSpace Available disk space to look for
     * @return A storage element meeting the requirements
     */
    public String retrieveStorageElementFromComputingElementWithDiskAvailable(
            String queue, String diskSpace) {
        String defaultSE = "";
        String storagePath = "";

        defaultSE = RQC.getStorageElementFromComputingElement(queue);
        storagePath = RQC.getStoragePathWithSpaceAvailFromDefaultStorageElement(defaultSE, diskSpace);

        return storagePath;
    }

    /**
     * Gets a subcluster at a site with given CPUs and memory.
     *
     * @param site The site
     * @param cluster The cluster
     * @param cpus Number of CPUs to look for
     * @param mem Available memory to look for
     * @return A list of subclusters meeting the requirements
     */
    public String[] retrieveSubClusterWithMemAndCPUsAtSite(String site,
            String cluster, String cpus, String mem) {
        return RQC.getSubClusterWithMemAndCPUsFromClusterFromSite(site, cluster, cpus, mem);
    }

    /**
     * Gets a list of the versions of a code that is available at a site.
     *
     * @param site The site to check
     * @param code The code to check
     * @return An array with versions of 'code' available at 'site'
     */
    public String[] retrieveCodeVersionsAtSite(String site, String code) {
        return RQC.getVersionsOfCodeAtSite(site, code);
    }

    /**
     * Gets the list of queues available at a given site.
     *
     * @param site The site that is being checked for queues
     * @return A list of queues available
     */
    public String[] retrieveQueueNamesAtSite(String site) {
        return RQC.getQueueNamesAtSite(site);
    }

    /**
     * Gets all codes available on the Grid.
     *
     * @return A list of all the codes available
     */
    public String[] retrieveAllSiteCodes() {
        return RQC.getAllCodesOnGrid();
    }

    /**
     * Gets a list of all the versions of specified code that are available on
     * the Grid.
     *
     * @param code The code to check for versions of
     * @return A list of all the version available
     */
    public String[] retrieveAllVersionsOfCodeOnGrid(String code) {
        return RQC.getAllVersionsOfCodeOnGrid(code);
    }

    /**
     * Returns a list of <code>SiteInfo</code> objects for all sites on the
     * Grid.
     *
     * @return An array of <code>SiteInfo</code> objects for all sites
     */
    public SiteInfo[] retrieveSiteStatus() {
        return RQC.getAllSitesStatus();
    }

    /**
     * Gets a list of all GridFTP servers available on the Grid. These are
     * used for data transfer.
     *
     * @return A list of GridFTP servers
     */
    public String[] retrieveAllGridFtpServersOnGrid() {
        return RQC.getAllGridFTPServersOnGrid();
    }

    /**
     * Gets the contact email address for a given site.
     *
     * @param site The site in question
     * @return the email address
     */
    public String getSiteContactEmailAtSite(String site) {
        return RQC.getSiteContactEmailAtSite(site);
    }

    /**
     * Initializes a grid proxy which will be used to authenticate the user
     * for all grid activities. Uses private key and certificate to generate
     * a proxy. These might have been obtained through a SLCS server.
     *
     * @param key The private key
     * @param certificate The certificate
     * @param lifetime Desired lifetime in seconds of the new proxy
     *
     * @return true if credentials were successfully created, false otherwise
     */
    public boolean initProxy(PrivateKey key, X509Certificate certificate,
                             int lifetime) {
        boolean retval = false;
        GlobusCredential proxy = null;
        int bits = 512;
        int proxyType = GSIConstants.DELEGATION_FULL;
        X509ExtensionSet extSet = null;
        BouncyCastleCertProcessingFactory factory =
            BouncyCastleCertProcessingFactory.getDefault();
        try {
            proxy = factory.createCredential(
                    new X509Certificate[] { certificate },
                    key, bits, lifetime, proxyType, extSet);
            credential = new GlobusGSSCredentialImpl(
                    proxy, GSSCredential.INITIATE_AND_ACCEPT);
            if (isProxyValid()) {
                logger.info("Acquired valid credentials.");
                retval = true;
            }
        } catch (Exception e) {
            logger.error("create user proxy error: "+e.toString(), e);
        }
        return retval;
    }

    /**
     * Initializes a grid proxy which will be used to authenticate the user
     * for all grid activities. Uses a username and password for MyProxy
     * authentication.
     *
     * @param proxyUser MyProxy username
     * @param proxyPass MyProxy password
     * @param lifetime  Desired lifetime in seconds of the new proxy
     * 
     * @return true if credentials were successfully created, false otherwise
     */
    public boolean initProxy(String proxyUser, String proxyPass, int lifetime) {
        boolean retval = false;
        try {
            credential = MyProxyManager.getDelegation(
                    myProxyServer, myProxyPort,
                    proxyUser, proxyPass.toCharArray(),
                    lifetime);

            if (isProxyValid()) {
                logger.info("Got credential from "+myProxyServer);
                retval = true;
            }
        } catch (Exception e) {
            logger.error("Could not get delegated proxy from server: " +
                    e.getMessage());
        }
        return retval;
    }

    /**
     * Initializes a grid proxy which will be used to authenticate the user
     * for all grid activities. This method requires an existing proxy file of
     * the current user.
     *
     * @return true if credentials were successfully created, false otherwise
     */
    public boolean initProxy() {
        boolean retval = false;
        try {
            GSSManager manager = ExtendedGSSManager.getInstance();
            credential = manager.createCredential(
                    GSSCredential.INITIATE_AND_ACCEPT);

            if (isProxyValid()) {
                retval = true;
            }
        } catch (GSSException e) {
            logger.error(FaultHelper.getMessage(e));
        }
        return retval;
    }

    /**
     * Checks the validity of currently set grid credentials. To be considered
     * valid, the grid proxy must exist and have a minimum remaining lifetime
     * (5 minutes by default).
     *
     * @return true if and only if the current credentials are valid
     */
    public boolean isProxyValid() {
        if (credential != null) {
            try {
                int lifetime = credential.getRemainingLifetime();
                logger.debug("Name: " + credential.getName().toString() +
                        ", Lifetime: " + lifetime + " seconds");
                if (lifetime > MIN_LIFETIME) {
                    return true;
                }
            } catch (GSSException e) {
                logger.error(FaultHelper.getMessage(e));
            }
        }
        return false;
    }
}

