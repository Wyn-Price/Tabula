package me.ichun.mods.tabula.client.gui.window;

import me.ichun.mods.ichunutil.client.gui.window.IWorkspace;
import me.ichun.mods.ichunutil.client.gui.window.Window;
import me.ichun.mods.ichunutil.client.gui.window.WindowPopup;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementButton;
import me.ichun.mods.ichunutil.common.module.tabula.formats.ImportList;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.tabula.client.core.ResourceHelper;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.client.gui.Theme;
import me.ichun.mods.tabula.client.gui.window.element.ElementListTree;
import me.ichun.mods.tabula.client.mainframe.core.ProjectHelper;
import me.ichun.mods.tabula.common.Tabula;
import net.minecraft.util.text.translation.I18n;
import org.apache.commons.io.FileUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WindowOpenProject extends Window
{
    public ElementListTree modelList;
    public ElementButton sortBy;
    public ElementButton sortDir;

    public File openingFile;
    public String openingJson;

    private boolean keyVLastDown;

    private int sortByType = 0; //0 -> name, 1 -> date, 2 -> size
    private boolean ascending = true;

    public WindowOpenProject(IWorkspace parent, int x, int y, int w, int h, int minW, int minH)
    {
        super(parent, x, y, w, h, minW, minH, "window.open.title", true);

        elements.add(sortDir = new ElementButton(this, 93, height - 22, 16, 16, 11, false, 1, 1, ""));
        elements.add(sortBy = new ElementButton(this, 10, height - 22, 80, 16, 10, false, 1, 1, ""));
        elements.add(new ElementButton(this, width - 140, height - 22, 60, 16, 1, false, 1, 1, "element.button.ok"));
        elements.add(new ElementButton(this, width - 70, height - 22, 60, 16, 0, false, 1, 1, "element.button.cancel"));
        modelList = new ElementListTree(this, BORDER_SIZE + 1, BORDER_SIZE + 1 + 10, width - (BORDER_SIZE * 2 + 2), height - BORDER_SIZE - 22 - 16, 3, false, false);
        elements.add(modelList);


        ArrayList<File> files = new ArrayList<>();
        Collections.addAll(files, ResourceHelper.getSaveDir().listFiles());

        File file = new File(ResourceHelper.getSaveDir(), "previous_opened_storage.dat");
        if(file.exists() && !file.isDirectory()) {
            try {
                for (String readLine : FileUtils.readLines(file, Charset.defaultCharset())) {
                    files.add(new File(readLine));
                }
            } catch (IOException e) {
                //Ignored
            }
        }

        for (File f : files) {
            if (!f.isDirectory() && ImportList.isFileSupported(f)) {
                modelList.createTree(null, f, 26, 0, false, false);
            }
        }
        this.sortFiles();

    }

    @Override
    public void draw(int mouseX, int mouseY) //4 pixel border?
    {
        super.draw(mouseX, mouseY);
        if(!minimized && openingFile != null)
        {
            workspace.getFontRenderer().drawString(I18n.translateToLocal("window.open.opening"), posX + 11, posY + height - 18, Theme.getAsHex(workspace.currentTheme.font), false);
        }
        boolean vDown = Keyboard.isKeyDown(Keyboard.KEY_V);
        if(GuiWorkspace.isCtrlKeyDown() && vDown && !this.keyVLastDown) {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if(transferable != null && transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    File data = new File(ResourceHelper.getSaveDir(), "previous_opened_storage.dat");
                    List<String> lines = FileUtils.readLines(data, Charset.defaultCharset());
                    for (File file : fileList) {
                        if(!lines.contains(file.getAbsolutePath())) {
                            lines.add(file.getAbsolutePath());
                        }
                        ProjectInfo project = ImportList.createProjectFromFile(file);
                        if(project != null) {
                            this.initiateProject(project, file, true);
                        }
                    }
                    FileUtils.writeLines(data, lines);
                } catch (Throwable ignored) {
                    //ignore
                }
            }
        }
        this.keyVLastDown = vDown;
    }

    @Override
    public void elementTriggered(Element element)
    {
        if(element.id == 0)
        {
            workspace.removeWindow(this, true);
        }
        if((element.id == 1 || element.id == 3) && openingFile == null && ((GuiWorkspace)workspace).isEditor)
        {
            for(int i = 0; i < modelList.trees.size(); i++)
            {
                ElementListTree.Tree tree = modelList.trees.get(i);
                if(tree.selected)
                {
                    if(workspace.windowDragged == this)
                    {
                        workspace.windowDragged = null;
                    }
                    ProjectInfo project = ImportList.createProjectFromFile((File)tree.attachedObject);
                    if(project == null)
                    {
                        workspace.addWindowOnTop(new WindowPopup(workspace, 0, 0, 180, 80, 180, 80, "window.open.failed").putInMiddleOfScreen());
                    }
                    else
                    {
                        this.initiateProject(project, (File) tree.attachedObject, false);
                    }
                    break;
                }
            }
        }
        if(element.id == 10) {
            sortByType++;
            sortByType%=3;
            this.sortFiles();
        }
        if(element.id == 11) {
            ascending = !ascending;
            this.sortFiles();
        }
    }

    private void sortFiles() {
        Comparator<File> comparator;
        switch (sortByType) {
            case 0:
                sortBy.text = "window.open.sort.name";
                comparator = Comparator.comparing(f -> f.getName().toLowerCase());
                break;
            case 1:
                sortBy.text = "window.open.sort.modified";
                comparator = Comparator.comparing(File::lastModified).reversed();
                break;
            case 2:
                sortBy.text = "window.open.sort.size";
                comparator = Comparator.comparing(File::length).reversed();
                break;
            default: throw new IllegalArgumentException("Impossible sortby type " + sortByType);
        }
        if(!ascending) {
            comparator = comparator.reversed();
        }

        sortDir.text = ascending ? "↑" : "↓";


        modelList.trees.sort(Comparator.comparing(tree -> (File) tree.attachedObject, comparator));
    }

    private void initiateProject(ProjectInfo project, File file, boolean pasted) {
        project.repair();

        openingFile = file;
        openingJson = project.getAsJson();
        ((GuiWorkspace)workspace).openNextNewProject = true;
        if(!((GuiWorkspace)workspace).remoteSession)
        {
            Tabula.proxy.tickHandlerClient.mainframe.overrideProject("", openingJson, project.bufferedTexture, pasted ? file : null);
        }
        else if(!((GuiWorkspace)workspace).sessionEnded)
        {
            ProjectHelper.sendProjectToServer(((GuiWorkspace)workspace).host, "", project, false);
        }
    }
}
