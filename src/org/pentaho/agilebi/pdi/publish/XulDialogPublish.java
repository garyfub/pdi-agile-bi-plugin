/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2009 Pentaho Corporation..  All rights reserved.
 */
package org.pentaho.agilebi.pdi.publish;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.agilebi.pdi.modeler.AbstractSwtXulDialogController;
import org.pentaho.agilebi.pdi.modeler.ModelServerPublish;
import org.pentaho.commons.util.repository.type.CmisObject;
import org.pentaho.commons.util.repository.type.PropertiesBase;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.gui.SpoonFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.platform.api.repository.ISolutionRepository;
import org.pentaho.platform.util.client.BiPlatformRepositoryClient;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingConvertor;
import org.pentaho.ui.xul.binding.BindingException;
import org.pentaho.ui.xul.binding.BindingExceptionHandler;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.binding.DefaultBindingFactory;
import org.pentaho.ui.xul.binding.Binding.Type;
import org.pentaho.ui.xul.components.WaitBoxRunnable;
import org.pentaho.ui.xul.components.XulButton;
import org.pentaho.ui.xul.components.XulCheckbox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.components.XulWaitBox;
import org.pentaho.ui.xul.containers.XulDialog;
import org.pentaho.ui.xul.containers.XulListbox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.swt.SwtBindingFactory;

/**
 * A dialog for publishing to a BI server
 * @author jamesdixon
 *
 */
public class XulDialogPublish extends AbstractSwtXulDialogController implements BindingExceptionHandler {

  private static Log logger = LogFactory.getLog(XulDialogPublish.class);
  
  private int folderTreeDepth = 99;
  
  private BindingFactory bf = new SwtBindingFactory();
  
  private XulListbox serverMenuList;
  
  private XulTextbox folderTextbox;
  
  private XulTree filesBox;
  
  private XulButton okButton;
  
  
  private XulCheckbox publishDatasourceCheck, publishModelCheck;
  
  private BiServerConfig biServerConfig;
  
  
  private ModelServerPublish publisher;

  private DatabaseMeta databaseMeta;
  
  private boolean publishDataSource = false;
  
  private boolean checkDatasources = false;
  
  private boolean accepted = false;
  
  private boolean datasourceExists = false;
  
  private CmisObject currentFolder;
  
  private String pathTemplate = "{path}{file}"; //$NON-NLS-1$ 
  
  private XulDialog biserverDialog;
  
  private XulDialogPublishModel publishModel;
  
  private XulDialog folderSelectionDialog;
  private XulTree folderTree;
  private XulButton folderSelectionDialogAccept;
  private boolean fileMode;
  
  // Connection form members
  private BiServerConnectionForm biserverForm = new BiServerConnectionForm();
  private XulTextbox publishpassword, password, userid, url, name;
  
  /**
   * Creates a new dialog. Gets the current list of BI server connections from the properties file
   * @param shell
   * @throws XulException
   */
  public XulDialogPublish( Shell shell ) throws XulException {
    super( shell ); 
    
    setName("dialog"); //$NON-NLS-1$

    // create the 'biserverlist' dialog
    initDialogController( "org/pentaho/agilebi/pdi/publish/res/publish.xul", "publish", null, null ); //$NON-NLS-1$ //$NON-NLS-2$
    
  }

  @Override
  protected Object getDialogResult() {
    return null;
  }

  @Override
  public Log getLogger() {
    return logger;
  }
  
  /**
   * Initializes the dialog. Finds the buttons so we can dis/enable them as 
   * needed. Displays the list of current connections. Sets the initial
   * buttons status.
   */
  public void init() {
    
    biServerConfig = BiServerConfig.getInstance();

    publishModel = new XulDialogPublishModel(biServerConfig);
    
    bf.setDocument(document);
    bf.setExceptionHandler(this);

    folderTextbox= (XulTextbox) document.getElementById("folderTextbox"); //$NON-NLS-1$
    serverMenuList = (XulListbox)document.getElementById("serverlist"); //$NON-NLS-1$
    filesBox = (XulTree)document.getElementById("filesBox"); //$NON-NLS-1$
    okButton = (XulButton) document.getElementById("publish_accept"); //$NON-NLS-1$
    publishDatasourceCheck = (XulCheckbox) document.getElementById("publishDatasource"); //$NON-NLS-1$
    publishModelCheck = (XulCheckbox) document.getElementById("publishModel"); //$NON-NLS-1$
    biserverDialog = (XulDialog) document.getElementById("biserverEditDialog");
    

    bf.setBindingType(Type.BI_DIRECTIONAL);
    
    
//    bf.createBinding(this, "selectedFolder", folderMenuList, "selectedItem");    //$NON-NLS-1$ //$NON-NLS-2$
    bf.createBinding(publishModel, "filename", "filename", "value");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$

    bf.setBindingType(Type.ONE_WAY);
    bf.createBinding(publishModel, "selectedFolder", folderTextbox, "value", new BindingConvertor<SolutionObject, String>(){
      @Override
      public String sourceToTarget(SolutionObject arg0) {
        String folderName = "";
        SolutionObject node = arg0;
        while(node != null && node.getCmisObject() != null){
          folderName = "/"+node.getCmisObject().findStringProperty(CmisObject.LOCALIZEDNAME, null) + folderName ;
          node = (SolutionObject) node.getParent();
        }
        return folderName;
      }
      @Override
      public SolutionObject targetToSource(String arg0) {return null;}
    });
    
    Binding serverListBinding = bf.createBinding(publishModel.getServerCollection(), "children", serverMenuList, "elements"); //$NON-NLS-1$ //$NON-NLS-2$

    bf.setBindingType(Binding.Type.BI_DIRECTIONAL);
    Binding serverListBindingIdx = bf.createBinding(publishModel, "selectedConnection", serverMenuList, "selectedItem");    //$NON-NLS-1$ //$NON-NLS-2$
    
    //foldersBinding = bf.createBinding(this, "folderNames", folderMenuList, "elements"); //$NON-NLS-1$ //$NON-NLS-2$
   
    
    
    // Edit Connection dialog members
    publishpassword = (XulTextbox) document.getElementById("publishpassword");
    password = (XulTextbox) document.getElementById("password");
    userid = (XulTextbox) document.getElementById("userid");
    url = (XulTextbox) document.getElementById("url");
    name = (XulTextbox) document.getElementById("name");
    bf.createBinding(biserverForm, "userId", userid, "value");
    bf.createBinding(biserverForm, "password", password, "value");
    bf.createBinding(biserverForm, "publishPassword", publishpassword, "value");
    bf.createBinding(biserverForm, "url", url, "value");
    bf.createBinding(biserverForm, "name", name, "value");
    
    bf.setBindingType(Binding.Type.ONE_WAY);
    bf.createBinding(serverMenuList, "selectedItem", this, "selectedServer");    //$NON-NLS-1$ //$NON-NLS-2$
    
    
    bf.createBinding(biserverForm, "valid", document.getElementById("biserverEditDialog_accept"), "!disabled");
    
    BindingConvertor btnConvertor = new BindingConvertor<BiServerConnection, Boolean>(){

      @Override
      public Boolean sourceToTarget(BiServerConnection arg0) {
        return arg0 == null;
      }

      @Override
      public BiServerConnection targetToSource(Boolean arg0) {
        return null;
      }
      
    };
    bf.createBinding(publishModel, "selectedConnection", "editServerBtn", "disabled", btnConvertor);    //$NON-NLS-1$ //$NON-NLS-2$
    bf.createBinding(publishModel, "selectedConnection", "deleteServerBtn", "disabled", btnConvertor);    //$NON-NLS-1$ //$NON-NLS-2$
    bf.createBinding(publishModel, "selectedConnection", "browseBtn", "disabled", btnConvertor);

    //folder selection dialog
    folderSelectionDialog = (XulDialog) document.getElementById("folderSelectionDialog");
    folderTree = (XulTree) document.getElementById("folderTree");
    folderSelectionDialogAccept = (XulButton) document.getElementById("folderSelectionDialog_accept");
    
    bf.createBinding(publishModel, "solutions", folderTree, "elements");
    
    bf.createBinding(folderTree, "selectedItem", folderSelectionDialogAccept, "disabled", new BindingConvertor<Object, Boolean>(){
      @Override
      public Boolean sourceToTarget(Object arg0) {
        return arg0 == null;
      }
      @Override
      public Object targetToSource(Boolean arg0) {
        return null;
      }
    });
    
    bf.createBinding(publishModel, "filename", publishModelCheck, "label", new BindingConvertor<String, String>(){
      @Override
      public String sourceToTarget(String value) {
        return BaseMessages.getString(getClass(), "Spoon.Perspectives.AgileBi.Publish.Model", value);
      }
      @Override
      public String targetToSource(String value) {
        return null;
      }
    });
    

    bf.createBinding(publishModel, "valid", "publish_accept", "!disabled");
    
    try {
      serverListBinding.fireSourceChanged();
      serverListBindingIdx.fireSourceChanged();
    } catch (Exception e) {
      e.printStackTrace();
    } 
  }
  
  public void setFilename(String fName){
    this.publishModel.setFilename(fName);
  }
  
  public String getFilename(){
    return this.publishModel.getFilename();
  }
  
  public BiServerConnection getBiServerConnection(){
    return this.publishModel.getSelectedConnection();
  }
  
  public void browseServer(){
    folderSelectionDialog.show();
  }
  
  public void folderAccept(){
    this.publishModel.setSelectedFolder((SolutionObject) folderTree.getSelectedItem());
    folderSelectionDialog.hide();
  }
  
  public void folderCancel(){
    folderSelectionDialog.hide();
  }
  
  protected void changePath() {
    StringBuilder sb = new StringBuilder();
    List<String> folders = new ArrayList<String>();
    // get all the parents of this folder
    if( currentFolder != null ) {
      CmisObject folder = currentFolder;
      while( folder != null ) {
        folders.add(0, folder.findStringProperty(CmisObject.LOCALIZEDNAME, null) );
        List<CmisObject> objects;
        try {
          objects = publisher.getNavigationService()
            .getFolderParent(BiPlatformRepositoryClient.PLATFORMORIG, folder.findIdProperty( PropertiesBase.OBJECTID, null ), null, false, false, false);
          if( objects != null && objects.size() > 0 ) {
            folder = objects.get(0);
          } else {
            folder = null;
            break;
          }
        } catch (Exception e) {
          folder = null;
          logger.error(e);
        }
      }
    }
    
    for( String folderName : folders ) {
      if( !"".equals( folderName ) ) { //$NON-NLS-1$
        sb.append( ISolutionRepository.SEPARATOR )
        .append( folderName );
      }
    }
    
    sb.append( ISolutionRepository.SEPARATOR );
    /*
    // create the folder path
    for( NamedObject folder : folderNames ) {
      if( folder instanceof NamedCmisObject ) {
        sb.append( ISolutionRepository.SEPARATOR )
        .append( folder.getName() );
      }
    }
    if( currentFolder != null ) {
      String currentFolderName = currentFolder.findStringProperty(CmisObject.LOCALIZEDNAME);
      if( !currentFolderName.equals( folderNames.get( folderNames.size()-1 ).getName()) ) {
        sb.append( ISolutionRepository.SEPARATOR )
        .append( currentFolderName );
      }
    }
    */
    String tmp = sb.toString();
    String path = pathTemplate.replace("{path}", tmp); //$NON-NLS-1$
    //path = path.replace("{file}", filename); //$NON-NLS-1$
    folderTextbox.setValue( path );
  }
  
    
  public void okClick() {
    
    publishDataSource = publishDatasourceCheck.isChecked();
    accepted = true;
    onDialogAccept();
  }

  public void cancelClick() {
    // perform an undo by reloading from the properties file
    onDialogCancel();
  }

  public void setFolderTreeDepth(int folderTreeDepth) {
    this.publishModel.setFolderTreeDepth(folderTreeDepth);
  }

  protected void connect() {
    
    // create the publisher object
    publisher = new ModelServerPublish();
    publisher.setBiServerConnection(publishModel.getSelectedConnection());
    publishModel.setSelectedFolder(null);

    // compare data sources
    try {
      int result = publisher.compareDataSourceWithRemoteConnection(databaseMeta);
      switch (result) {
        case ModelServerPublish.REMOTE_CONNECTION_SAME : {
          //datasourceLabel.setValue( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.DatasourceSame") ); //$NON-NLS-1$
          publishDatasourceCheck.setChecked( false );
          datasourceExists = true;
          break;
        }
        case ModelServerPublish.REMOTE_CONNECTION_MISSING : {
          //datasourceLabel.setValue( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.DatasourceMissing") ); //$NON-NLS-1$
          datasourceExists = false;
          break;
        }
        case ModelServerPublish.REMOTE_CONNECTION_MUST_BE_JNDI : {
          //datasourceLabel.setValue( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.DatasourceCannotPublish") ); //$NON-NLS-1$
          publishDatasourceCheck.setChecked( false );
          datasourceExists = false;
          break;
        }
        case ModelServerPublish.REMOTE_CONNECTION_DIFFERENT : {
          //datasourceLabel.setValue( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.DatasourceDifferent") ); //$NON-NLS-1$
          datasourceExists = true;
          break;
        }
      }
    } catch (Exception e) {
      logger.error(e);
      SpoonFactory.getInstance().messageBox( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.CouldNotGetDataSources", e.getLocalizedMessage() ),  //$NON-NLS-1$
          BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.Error"), false, Const.ERROR); //$NON-NLS-1$
    }      
    

  }
  
  public boolean isExistentDatasource() {
    return datasourceExists;
  }
  
  public void setSelectedServer(BiServerConnection selectedServer){
    currentFolder = null;
    //datasourceLabel.setValue( BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.DatasourceUnknown") ); //$NON-NLS-1$
    if ( selectedServer != null ) {
      
      try{
        XulWaitBox wait = (XulWaitBox) document.createElement("waitbox");
        wait.setTitle(BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.Connect.Title"));
        wait.setMessage(BaseMessages.getString(this.getClass(),"Spoon.Perspectives.AgileBi.Publish.Connect.Message"));
        wait.setIndeterminate(true);
        wait.setRunnable(new WaitBoxRunnable(wait){

          @Override
          public void cancel() {          
          }

          @Override
          public void run() {
            connect();
            this.waitBox.stop();
          }
          
        });
        wait.start();
      } catch(XulException e){ //could not create the wait dialog
        logger.debug(e);
        connect();
      }
      
    }
  }


  private boolean addingNewServer;
  public void editServer() {
    addingNewServer = false;
    biserverForm.setConn(this.publishModel.getSelectedConnection());
    biserverDialog.show();
  }
  
  public void newServer(){
    addingNewServer = true;
    biserverForm.setConn(new BiServerConnection());
    biserverDialog.show();
    biServerConfig.save();
  }
  
  public void deleteServer(){
    biServerConfig.getServerConnections().remove(this.publishModel.getSelectedConnection());
    biServerConfig.save();
  }
  
  public void testServerConnection(){
    BiServerConfigUtil.testServerConnection(this.biserverForm.getConnectionCopy());
  }
  
  public void biserverCancel(){
    this.biserverDialog.hide();
  }
  
  public void biserverAccept(){
    this.biserverDialog.hide();
    BiServerConnection conn = biserverForm.saveConn();
    if(addingNewServer){
      biServerConfig.getServerConnections().add(conn);
    }
    biServerConfig.save();
    
  }

  public DatabaseMeta getDatabaseMeta() {
    return databaseMeta;
  }

  public void setDatabaseMeta(DatabaseMeta databaseMeta) {
    this.databaseMeta = databaseMeta;

    XulCheckbox datasourceCheck = (XulCheckbox) document.getElementById("publishDatasource");
    datasourceCheck.setLabel(BaseMessages.getString(getClass(), "Spoon.Perspectives.AgileBi.Publish.Data", databaseMeta.getName()));
  }


  public boolean isPublishDataSource() {
    return publishDataSource;
  }

  public void setPublishDataSource(boolean publishDataSource) {
    this.publishDataSource = publishDataSource;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getPath() {
   
    if( publishModel.getSelectedFolder() == null ) {
      return null;
    } else {
      // validate the user supplied string
      // TODO: implement adhoc path
      return publishModel.getNavigationService().getRepositoryPath(publishModel.getSelectedFolder().getCmisObject());
    }
    
  }

  public void setShowLocation(boolean showServerSelection, boolean showFolders, boolean showCurrentFolder ) {
    XulComponent component = document.getElementById("serverControls"); //$NON-NLS-1$
    component.setVisible( showServerSelection );
    //component = document.getElementById("folderControls"); //$NON-NLS-1$
    //component.setVisible( showFolders );
    //component = document.getElementById("currentFolderControls"); //$NON-NLS-1$
    //component.setVisible( showCurrentFolder );
    component = document.getElementById("locationGroupBox"); //$NON-NLS-1$
    component.setVisible( showServerSelection || showFolders || showCurrentFolder );
  }

  public void setShowDatasourceStatus(boolean showDatasourceStatus) {
    XulComponent component = document.getElementById("datasourceGroupBox"); //$NON-NLS-1$
    component.setVisible( showDatasourceStatus );
  }

  public String getPathTemplate() {
    return pathTemplate;
  }

  public void setPathTemplate(String pathTemplate) {
    this.pathTemplate = pathTemplate;
  }

  public void setCheckDatasources(boolean checkDatasources) {
    this.checkDatasources = checkDatasources;
  }

  public void handleException(BindingException t) {
    PublishException ex = extractPublishException(t);
    if(ex == null){
      logger.error(t);
      t.printStackTrace();
      return;
    }
    logger.error(ex);
    try {
      XulMessageBox msg = (XulMessageBox) document.createElement("messagebox");
      msg.setTitle(BaseMessages.getString(getClass(), "connection.error.title"));
      msg.setMessage(BaseMessages.getString(getClass(), "connection.error.message", ex.getMessage()));
      msg.setIcon(SWT.ERROR_IO);
      msg.open();
    } catch (XulException e) {
    }
    
  }
  
  private PublishException extractPublishException(BindingException e){
    Throwable curr = e;
    while(curr != null && curr instanceof PublishException == false){
      curr = curr.getCause();
    }
    return (PublishException) ((curr instanceof PublishException)? curr : null);
  }
  
  public boolean isPublishXmi(){
    return publishModel.isPublishXmi();
  }
  
  public void setFileMode(boolean b){
    this.fileMode = b;
    publishModelCheck.setVisible(b);
  }
}
