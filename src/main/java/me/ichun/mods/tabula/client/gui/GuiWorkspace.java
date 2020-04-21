package me.ichun.mods.tabula.client.gui;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import me.ichun.mods.ichunutil.client.gui.window.IWorkspace;
import me.ichun.mods.ichunutil.client.gui.window.Window;
import me.ichun.mods.ichunutil.client.gui.window.WindowPopup;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementMinimize;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementToggle;
import me.ichun.mods.ichunutil.client.render.RendererHelper;
import me.ichun.mods.ichunutil.common.core.util.IOUtil;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.Animation;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.AnimationComponent;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeGroup;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import me.ichun.mods.tabula.client.core.ModelSelector;
import me.ichun.mods.tabula.client.core.ResourceHelper;
import me.ichun.mods.tabula.client.gui.window.*;
import me.ichun.mods.tabula.client.gui.window.element.ElementListTree;
import me.ichun.mods.tabula.client.mainframe.core.ProjectHelper;
import me.ichun.mods.tabula.client.model.ModelVoxel;
import me.ichun.mods.tabula.common.Tabula;
import me.ichun.mods.tabula.common.packet.PacketEndSession;
import me.ichun.mods.tabula.common.packet.PacketGenericMethod;
import me.ichun.mods.tabula.common.packet.PacketRemoveListener;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.translation.I18n;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.core.util.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.Project;

import javax.imageio.ImageIO;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.Function;

public class GuiWorkspace extends IWorkspace
{
    public float renderTick;

    public int oriScale;
    public final boolean remoteSession;
    public boolean isEditor;
    public boolean sessionEnded;
    public String host;
    public int hostX;
    public int hostY;
    public int hostZ;

    public ModelVoxel voxel;
    public static final ResourceLocation txVoxel = new ResourceLocation("tabula", "textures/model/cube.png");

    public static final ResourceLocation grid16 = new ResourceLocation("tabula", "textures/workspace/grid16.png");
    public static final ResourceLocation orientationBase = new ResourceLocation("tabula", "textures/workspace/orientationbase.png");

    public ArrayList<String> editors = new ArrayList<>();
    public ArrayList<String> listeners = new ArrayList<>();

    public boolean keyDeleteDown;
    public boolean keyXDown;
    public boolean keyCDown;
    public boolean keyVDown;
    public boolean keyZDown;
    public boolean keyYDown;
    public boolean keySDown;
    public boolean keyNDown;
    public boolean keyODown;
    public boolean keyHomeDown;
    public boolean keyEndDown;
    public boolean keyTabDown;

    public Object cubeCopied;

    public WindowProjectSelection projectManager;
    public WindowControls windowControls;
    public WindowTexture windowTexture;
    public WindowModelTree windowModelTree;
    public WindowChat windowChat;
    public WindowAnimate windowAnimate;

    public boolean init;

    public float cameraZoom = 1.0F;
    public int cameraZoomInertia = 0;
    public float cameraZoomPerScroll = 0.05F;

    public float cameraFov = 30F;
    public int cameraFovInertia = 0;
    public float cameraFovPerScroll = 1F;

    public float cameraYaw;
    public float cameraPitch;
    public float cameraOffsetX;
    public float cameraOffsetY;

    public boolean clicked;
    public int prevMouseX;
    public int prevMouseY;

    public int controlDrag = -1;
    public int controlDragX;
    public int controlDragY;

    public boolean wantToExit;

    public boolean vertexSnapping;
    public Pair<Integer, String> anchorSelectedVertex = Pair.of(-1, "");

    public boolean openNextNewProject;

    private ModelSelector modelSelector = new ModelSelector(this, 256);

    public GuiWorkspace(int scale, boolean remote, boolean editing, String name, int i, int j, int k)
    {
        levels = new ArrayList<ArrayList<Window>>() {{
            add(0, new ArrayList<>()); // dock left
            add(1, new ArrayList<>()); // dock right
            add(2, new ArrayList<>()); // dock btm
            add(3, new ArrayList<>()); // dock top
            add(4, new ArrayList<>()); // chat level
        }};

        oriScale = scale;

        tooltipTime = Tabula.config.tooltipTime;

        voxel = new ModelVoxel();

        File defaultTheme = new File(ResourceHelper.getThemesDir(), "default.json");

        Theme theme = new Theme();

        try
        {
            InputStream con = new FileInputStream(defaultTheme);
            String data = new String(ByteStreams.toByteArray(con));
            con.close();

            Theme.loadTheme(theme, (new Gson()).fromJson(data, Theme.class));
        }
        catch(Exception e)
        {
            Tabula.LOGGER.warn("Error reading default theme!");
            e.printStackTrace();
        }

        String fav = Tabula.config.favTheme;
        if(!(fav.isEmpty() || fav.equalsIgnoreCase("default")))
        {
            File userTheme = new File(ResourceHelper.getThemesDir(), fav + ".json");

            if(userTheme.exists())
            {
                try
                {
                    InputStream con = new FileInputStream(userTheme);
                    String data = new String(ByteStreams.toByteArray(con));
                    con.close();

                    Theme.loadTheme(theme, (new Gson()).fromJson(data, Theme.class));
                }
                catch(Exception e)
                {
                    Tabula.LOGGER.warn("Error reading preferred theme!");
                    e.printStackTrace();
                }
            }
            else
            {
                Tabula.LOGGER.warn("Preferred theme file does not exist!");
            }
        }

        currentTheme = theme;
        remoteSession = remote;
        isEditor = editing;
        host = name;
        hostX = i;
        hostY = j;
        hostZ = k;

        //        renderBlocks = new RenderBlocks();

        levels.get(3).add(new WindowTopDock(this, width, 20));
        projectManager = new WindowProjectSelection(this, width, 20);
        levels.get(3).add(projectManager);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        if(!init)
        {
            windowControls = new WindowControls(this, width / 2 - 80, height / 2 - 125, 162, 295, 162, 295);
            windowTexture = new WindowTexture(this, width / 2 - 53, height / 2 - 100, 106, 100, 106, 88);
            windowModelTree = new WindowModelTree(this, width / 2 - 53, height / 2 - 125, 106, 250, 106, 250);
            windowAnimate = new WindowAnimate(this, 0, 0, 100, 100, 100, 100);
            addToDock(0, windowControls);
            addToDock(1, windowTexture);
            addToDock(1, windowModelTree);
            addToDock(2, windowAnimate);
            windowAnimate.toggleMinimize();

            windowChat = new WindowChat(this, -1000, -1000, 250, 180, 162, 50);
            levels.get(4).add(windowChat);

            if(host != null)
            {
                if(host.equals(Minecraft.getMinecraft().getSession().getUsername()))
                {
                    ProjectHelper.addSystemMessage(I18n.translateToLocal("system.hosting"));
                }
                else
                {
                    ProjectHelper.addSystemMessage(I18n.translateToLocalFormatted("system.hostingOther", host));
                }
                windowChat.toggleVisibility();

                String[] chatSettings = Tabula.config.chatWindow.split(":");
                if(chatSettings.length == 8)
                {
                    try
                    {
                        windowChat.posX = Integer.parseInt(chatSettings[0]);
                        windowChat.posY = Integer.parseInt(chatSettings[1]);
                        windowChat.width = Integer.parseInt(chatSettings[2]);
                        windowChat.height = Integer.parseInt(chatSettings[3]);
                        if(Integer.parseInt(chatSettings[4]) >= 0)
                        {
                            addToDock(Integer.parseInt(chatSettings[4]), windowChat);
                        }
                        if(Boolean.parseBoolean(chatSettings[5]))
                        {
                            windowChat.toggleMinimize();
                        }
                        windowChat.oriWidth = Integer.parseInt(chatSettings[6]);
                        windowChat.oriHeight = Integer.parseInt(chatSettings[7]);
                    }
                    catch(Exception ignored){}
                    windowChat.resized();
                }
            }
            init = true;
        }
    }

    @Override
    public void updateScreen()
    {
        if(!init)
        {
            return;
        }
        super.updateScreen();
        if(cameraFovInertia > 0)
        {
            cameraFovInertia--;
            cameraFov += cameraFovPerScroll * (double)cameraFovInertia / 10D;
        }
        else if(cameraFovInertia < 0)
        {
            cameraFovInertia++;
            cameraFov += cameraFovPerScroll * (double)cameraFovInertia / 10D;
        }
        if(cameraZoomInertia > 0)
        {
            cameraZoomInertia--;
            cameraZoom += cameraZoomPerScroll * ((cameraZoom < 1.0F) ? ((cameraZoom + 0.5F) / 1.5F) : 1.0F ) * ((double)cameraZoomInertia / 10D);
        }
        else if(cameraZoomInertia < 0)
        {
            cameraZoomInertia++;
            cameraZoom += cameraZoomPerScroll * ((cameraZoom < 1.0F) ? ((cameraZoom + 0.5F) / 1.5F) : 1.0F ) * ((double)cameraZoomInertia / 10D);
        }
        for(ProjectInfo proj : projectManager.projects)
        {
            if(liveTime - proj.lastAutosave > 20 * 60 * 5 && !proj.autosaved)
            {
                File file = new File(ResourceHelper.getAutosaveDir(), proj.modelName + "-TabulaAutosave-" + Minecraft.getSystemTime() + ".tbl");
                if(ProjectInfo.saveProject(proj, file))
                {
                    //get the last few files in this name, delete them off.
                    long timestamp = 0;
                    File oldestAutosave = null;
                    int count = 0;
                    File[] files = ResourceHelper.getAutosaveDir().listFiles();
                    for(File save : files)
                    {
                        if(!save.isDirectory() && save.getName().endsWith(".tbl") && save.getName().startsWith(proj.modelName + "-TabulaAutosave-"))
                        {
                            count++;
                            String stamp = save.getName().substring((proj.modelName + "-TabulaAutosave-").length(), save.getName().length() - 4);//remove the ".tbl"
                            try
                            {
                                long time = Long.parseLong(stamp);
                                if(time < timestamp || timestamp == 0)
                                {
                                    timestamp = time;
                                    oldestAutosave = save;
                                }
                            }
                            catch(NumberFormatException e)
                            {
                            }
                        }
                    }
                    if(oldestAutosave != null && count > 5)
                    {
                        oldestAutosave.delete();
                    }
                }
                proj.lastAutosave = liveTime;
                proj.autosaved = true;
            }
            for(Animation anim : proj.anims)
            {
                anim.update();
            }
        }
        if(wantToExit)
        {
            if(!remoteSession)
            {
                boolean canClose = true;
                for(int i = levels.size() - 1; i >= 0 ; i--)
                {
                    for(int j = levels.get(i).size() - 1; j >= 0; j--)
                    {
                        Window window = levels.get(i).get(j);
                        if(window instanceof WindowSaveBeforeClosing || window instanceof WindowSaveAs)
                        {
                            canClose = false;
                        }
                    }
                }
                if(canClose)
                {
                    for(int i = projectManager.projects.size() - 1; i >= 0; i--)
                    {
                        ProjectInfo project = projectManager.projects.get(i);
                        closeProject(project);
                        if(!project.saved)
                        {
                            canClose = false;
                            break;
                        }
                        else
                        {
                            project.destroy();
                        }
                    }
                    if(canClose)
                    {
                        this.mc.displayGuiScreen(null);
                        this.mc.setIngameFocus();
                    }
                }
            }
            else
            {
                this.mc.displayGuiScreen(null);
                this.mc.setIngameFocus();
            }
        }
    }

    @Override
    public boolean canClickOnElement(Window window, Element element)
    {
        return !(projectManager.projects.isEmpty() && !window.interactableWhileNoProjects() && !(element instanceof ElementMinimize));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float renderTick)
    {
        this.renderTick = renderTick;
        if(!init)
        {
            return;
        }
        //TODO a reset all windows button for people who "accidentally" drag the window out of the screen
        //TODO Do not close window if it is docked?
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.pushMatrix();
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableNormalize();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        if(cameraFov < 15F)
        {
            cameraFov = 15F;
        }
        else if(cameraFov > 160F)
        {
            cameraFov = 160F;
        }

        ScaledResolution resolution = new ScaledResolution(mc);

        if (Mouse.isButtonDown(0) && !mouseLeftDown)
        {
            //Render the "furnace"
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, -5000.0D, 5000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.pushMatrix();
            Minecraft.getMinecraft().renderEngine.bindTexture(txVoxel);
            GlStateManager.translate(width - (levels.get(1).isEmpty() ? 15F : 15F + levels.get(1).get(0).width), height - 15F - windowAnimate.getHeight(), 3000F);
            float scale = 15F;
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            GlStateManager.rotate(-15F + cameraPitch + 180F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-38F + cameraYaw, 0.0F, 1.0F, 0.0F);
            float scale1 = 16F;
            GlStateManager.scale(scale1, scale1, scale1);
            GlStateManager.clearColor(0F, 0F, 0F, 255F);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
            voxel.render(0.0625F);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(3);
            GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL11.GL_RGB, GL11.GL_FLOAT, buffer);
            int blue = Math.round(buffer.get(2) * 255F);
            if(blue > 0)
            {
                blue -= 250;
                if(blue <= 1)
                {
                    cameraYaw = 0F;
                }
                else
                {
                    cameraPitch = 0F;
                }
                switch(blue)
                {
                    case 0:
                    {
                        cameraPitch = 90F;
                        break;
                    }
                    case 1:
                    {
                        cameraPitch = -90F;
                        break;
                    }
                    case 2:
                    {
                        cameraYaw = 90F;
                        break;
                    }
                    case 3:
                    {
                        cameraYaw = 270F;
                        break;
                    }
                    case 4:
                    {
                        cameraYaw = 180F;
                        break;
                    }
                    case 5:
                    {
                        cameraYaw = 0F;
                        break;
                    }
                }
                cameraPitch += 15F;
                cameraYaw += 38F;
            }
            GlStateManager.popMatrix();
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        Project.gluPerspective(cameraFov, (float)(resolution.getScaledWidth_double() / resolution.getScaledHeight_double()), 1F, 10000F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        GlStateManager.clearColor(0F, 0F, 0F, 255F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);

        Pair<Integer, String> mouseOver = Pair.of(-1, "");
        if(vertexSnapping) {
            mouseOver = renderVertexSnapping(true, -1);
        }

        if (Mouse.isButtonDown(0) && !mouseLeftDown) {
            if(mouseOver.getLeft() != -1) {
                boolean already = this.anchorSelectedVertex.getLeft() != -1;
                if(already) {
                    if(!this.anchorSelectedVertex.getRight().equals(mouseOver.getRight())) {
                        Optional<Vec3d> anchorO = getPosition(mouseOver, this::getForIdentifier);
                        Optional<CubeInfo> toMoveO = getForIdentifier(this.anchorSelectedVertex.getRight());
                        if(anchorO.isPresent() && toMoveO.isPresent()) {
                            Vec3d anchor = anchorO.get();

                            CubeInfo toMove = toMoveO.get();
                            Vec3d toMovePosition = getPosition(this.anchorSelectedVertex, this::getForIdentifier).orElseThrow(IllegalArgumentException::new);

                            Vec3d toMoveDiff = toMovePosition.subtract(anchor);

                            Point3d point = new Point3d(toMoveDiff.x, toMoveDiff.y, toMoveDiff.z);

                            getForIdentifier(toMove.parentIdentifier).ifPresent(c -> rotatePoint(point, c, this::getForIdentifier));

                            toMove.position[0] -= point.x * 16F;
                            toMove.position[1] += point.y * 16F;
                            toMove.position[2] += point.z * 16F;

                            this.updateCube(toMove);
                        }

                        this.anchorSelectedVertex = Pair.of(-1, "");
                    }

                } else {
                    this.anchorSelectedVertex = mouseOver;
                }
            } else {
                Object object = windowControls.selectedObject;
                modelSelector.onClick(mouseX, mouseY, this.vertexSnapping);
                if(object != null && windowControls.selectedObject == null) {
                    this.anchorSelectedVertex = Pair.of(-1, "");
                }
            }
        }

        GlStateManager.clearColor((float)currentTheme.workspaceBackground[0] / 255F, (float)currentTheme.workspaceBackground[1] / 255F, (float)currentTheme.workspaceBackground[2] / 255F, 255F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        if(vertexSnapping) {
            renderVertexSnapping(false, mouseOver.getLeft());
        }

        renderWorkspace(mouseX, mouseY, renderTick);

        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        boolean onWindow = drawWindows(mouseX, mouseY);

        if(clicked)
        {
            if(GuiScreen.isShiftKeyDown() || Mouse.isButtonDown(2))
            {
                float factor = 0.0125F;
                cameraOffsetX += (prevMouseX - mouseX) * factor;
                cameraOffsetY -= (prevMouseY - mouseY) * factor;
            }
            else
            {
                if(GuiScreen.isCtrlKeyDown())
                {
                    float factor = 0.0125F;
                    cameraZoom += (prevMouseY - mouseY) * factor;
                }
                else
                {
                    float factor = 0.5F;
                    cameraYaw -= (prevMouseX - mouseX) * factor;
                    cameraPitch += (prevMouseY - mouseY) * factor;
                }
            }

            prevMouseX = mouseX;
            prevMouseY = mouseY;
        }
        int scroll = Mouse.getDWheel();
        if(!onWindow)
        {
            if(scroll != 0)
            {
                if(GuiScreen.isShiftKeyDown())
                {
                    cameraFov += cameraFovPerScroll * (scroll / 120F);
                    cameraFovInertia = scroll > 0 ? 10 : -10;
                }
                else
                {
                    cameraZoom += cameraZoomPerScroll * ((cameraZoom < 1.0F) ? ((cameraZoom + 0.5F) / 1.5F) : 1.0F) * (scroll / 120F);
                    cameraZoomInertia = scroll > 0 ? 10 : -10;
                }
            }

            if(Mouse.isButtonDown(1) && !mouseRightDown || Mouse.isButtonDown(2) && !mouseMiddleDown)
            {
                clicked = true;
                prevMouseX = mouseX;
                prevMouseY = mouseY;
            }
        }
        if(!Mouse.isButtonDown(1) && !Mouse.isButtonDown(2))
        {
            clicked = false;
        }

        updateElementHovered(mouseX, mouseY, scroll);

        if(elementSelected == null)
        {
            if(Keyboard.isKeyDown(Keyboard.KEY_DELETE) && !keyDeleteDown)
            {
                windowControls.selectedObject = null;
                windowControls.refresh = true;

                for(Element e : windowModelTree.elements)
                {
                    if(e.id == 2)
                    {
                        windowModelTree.elementTriggered(e);
                    }
                }
            }
            if(GuiScreen.isCtrlKeyDown())
            {
                if(Keyboard.isKeyDown(Keyboard.KEY_X) && !keyXDown)
                {
                    cut();
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_C) && !keyCDown)
                {
                    copy();
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_V) && !keyVDown && cubeCopied != null && !isPasteBlocked())
                {
                    paste(GuiScreen.isShiftKeyDown(), true);
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_Z) && !keyZDown || Keyboard.isKeyDown(Keyboard.KEY_Y) && !keyYDown)
                {
                    switchState(Keyboard.isKeyDown(Keyboard.KEY_Y) || !GuiScreen.isShiftKeyDown());
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_S) && !keySDown)
                {
                    if(GuiScreen.isShiftKeyDown())
                    {
                        this.addWindowOnTop(new WindowSaveAs(this, 0, 0, 200, 100, 200, 100, false).putInMiddleOfScreen());
                    }
                    else
                    {
                        save(false);
                    }
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_N) && !keyNDown)
                {
                    this.addWindowOnTop(new WindowNewProject(this, this.width / 2 - 100, this.height / 2 - 80, 200, 160, 200, 160).putInMiddleOfScreen());
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_O) && !keyODown)
                {
                    this.addWindowOnTop(new WindowOpenProject(this, this.width / 2 - 130, this.height / 2 - 160, 260, 320, 240, 160).putInMiddleOfScreen());
                }
                if(Keyboard.isKeyDown(Keyboard.KEY_TAB) && !keyTabDown)
                {
                    if(!projectManager.projects.isEmpty())
                    {
                        projectManager.selectedProject++;
                        if(projectManager.selectedProject >= projectManager.projects.size())
                        {
                            projectManager.selectedProject = 0;
                        }
                        projectManager.changeProject(projectManager.selectedProject);
                    }
                }
            }
            else if(Keyboard.isKeyDown(Keyboard.KEY_TAB) && host != null)
            {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0F, 0F, 20F * (levels.size() + 1));

                ArrayList<String> listenersList = remoteSession ? listeners : new ArrayList<>(Tabula.proxy.tickHandlerClient.mainframe.listeners.keySet());
                ArrayList<String> editorsList = remoteSession ? editors : Tabula.proxy.tickHandlerClient.mainframe.editors;
                listenersList.removeAll(editorsList);

                int tabHeight = (fontRenderer.FONT_HEIGHT + 2) * (listenersList.size() + editorsList.size() + 1) + 3 + 2; // border


                RendererHelper.drawColourOnScreen(currentTheme.windowBorder[0], currentTheme.windowBorder[1], currentTheme.windowBorder[2], 255, width / 2 - 101, (height - tabHeight) / 2 - 1, 202, tabHeight + 2    , 0);
                RendererHelper.drawColourOnScreen(currentTheme.windowBackground[0], currentTheme.windowBackground[1], currentTheme.windowBackground[2], 255, width / 2 - 100, (height - tabHeight) / 2, 200, tabHeight, 0);
                RendererHelper.drawColourOnScreen(currentTheme.windowBorder[0], currentTheme.windowBorder[1], currentTheme.windowBorder[2], 255, width / 2 - 101, (height - tabHeight) / 2 - 1 + fontRenderer.FONT_HEIGHT + 2 + 3, 202, 1, 0);

                fontRenderer.drawString(I18n.translateToLocal("system.playersInSession"), width / 2 - 101 + 4, (height - tabHeight) / 2 - 1 + 3, currentTheme.getAsHex(currentTheme.font), false);

                for(int i = 0; i < editorsList.size(); i++)
                {
                    fontRenderer.drawString(editorsList.get(i), width / 2 - 101 + 4, (height - tabHeight) / 2 - 1 + 3 + (fontRenderer.FONT_HEIGHT + 2) + 4, currentTheme.getAsHex(currentTheme.font), false);
                    fontRenderer.drawString(i == 0 ? I18n.translateToLocal("system.host") : I18n.translateToLocal("system.editor"), width / 2 + 101 - 4 - fontRenderer.getStringWidth(i == 0 ? I18n.translateToLocal("system.host") : I18n.translateToLocal("system.editor")), (height - tabHeight) / 2 - 1 + 3 + (fontRenderer.FONT_HEIGHT + 2) + 4, currentTheme.getAsHex(currentTheme.font), false);
                    GlStateManager.translate(0F, (fontRenderer.FONT_HEIGHT + 2), 0);
                }
                for(String aListenersList : listenersList)
                {
                    fontRenderer.drawString(aListenersList, width / 2 - 101 + 4, (height - tabHeight) / 2 - 1 + 3 + (fontRenderer.FONT_HEIGHT + 2) + 4, currentTheme.getAsHex(currentTheme.font), false);
                    GlStateManager.translate(0F, (fontRenderer.FONT_HEIGHT + 2), 0);
                }

                GlStateManager.popMatrix();
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_HOME) && !keyHomeDown || !Keyboard.isKeyDown(Keyboard.KEY_HOME) && keyHomeDown)
            {
                windowAnimate.timeline.setCurrentPos(0);
                windowAnimate.timeline.focusOnTicker();
            }
            if(Keyboard.isKeyDown(Keyboard.KEY_END) && !keyEndDown || !Keyboard.isKeyDown(Keyboard.KEY_END) && keyEndDown)
            {
                windowAnimate.timeline.setCurrentPos(0);
                if(!windowAnimate.animList.selectedIdentifier.isEmpty())
                {
                    Animation anim = (Animation)windowAnimate.animList.getObjectByIdentifier(windowAnimate.animList.selectedIdentifier);
                    if(anim != null)
                    {
                        windowAnimate.timeline.setCurrentPos(anim.getLength());
                    }
                }
                windowAnimate.timeline.focusOnTicker();
            }
        }

        GlStateManager.popMatrix();

        updateKeyStates();

        updateWindowDragged(mouseX, mouseY);

        updateElementDragged(mouseX, mouseY);

        if(controlDrag >= 0 && !this.vertexSnapping)
        {
            if(Mouse.isButtonDown(0))
            {
                ArrayList<me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree> trees = windowModelTree.modelList.trees;

                for (me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree tree : trees)
                {
                    if (tree.selected && tree.attachedObject instanceof CubeInfo)
                    {
                        CubeInfo cube = (CubeInfo)tree.attachedObject;

                        boolean changed = false;

                        int changeAmt = Math.abs(controlDragY - mouseY) < Math.abs(mouseX - controlDragX) ? mouseX - controlDragX : controlDragY - mouseY;

                        if(controlDrag < 3)
                        {
                            changed = true;
                            if(controlDrag == 2)
                            {
                                cube.rotation[1] += changeAmt * 1D / cameraZoom;
                            }
                            else if(controlDrag == 1)
                            {
                                cube.rotation[2] += changeAmt * 1D / cameraZoom;
                            }
                            else if(controlDrag == 0)
                            {
                                cube.rotation[0] += changeAmt * 1D / cameraZoom;
                            }
                            controlDragX = mouseX;
                            controlDragY = mouseY;
                        }
                        else if(Math.abs(changeAmt) > 6)
                        {
                            int dimChange = Math.round(changeAmt / ((float)6 * cameraZoom));
                            if(controlDrag <= 4)
                            {
                                if(cube.dimensions[1] + dimChange >= 0)
                                {
                                    cube.dimensions[1] += dimChange;
                                    if(controlDrag == 4)
                                    {
                                        cube.offset[1] -= dimChange;
                                    }
                                }
                            }
                            else if(controlDrag <= 6)
                            {
                                if(cube.dimensions[0] + dimChange >= 0)
                                {
                                    cube.dimensions[0] += dimChange;
                                    if(controlDrag == 6)
                                    {
                                        cube.offset[0] -= dimChange;
                                    }
                                }
                            }
                            else if(controlDrag <= 8)
                            {
                                if(cube.dimensions[2] + dimChange >= 0)
                                {
                                    cube.dimensions[2] += dimChange;
                                    if(controlDrag == 8)
                                    {
                                        cube.offset[2] -= dimChange;
                                    }
                                }
                            }
                            changed = true;
                            controlDragX = mouseX;
                            controlDragY = mouseY;
                        }

                        if(changed)
                        {
                            Gson gson = new Gson();
                            String s = gson.toJson(cube);
                            if(!this.remoteSession)
                            {
                                Tabula.proxy.tickHandlerClient.mainframe.updateCube(this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, this.windowAnimate.animList.selectedIdentifier, this.windowAnimate.timeline.selectedIdentifier, this.windowAnimate.timeline.getCurrentPos());
                            }
                            else if(!this.sessionEnded && this.isEditor)
                            {
                                Tabula.channel.sendToServer(new PacketGenericMethod(this.host, "updateCube", this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, this.windowAnimate.animList.selectedIdentifier, this.windowAnimate.timeline.selectedIdentifier, this.windowAnimate.timeline.getCurrentPos()));
                            }
                        }

                        break;
                    }
                }

            }
            else
            {
                controlDrag = -1;
            }
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        if(false && projectManager.selectedProject != -1 && !Keyboard.isKeyDown(Keyboard.KEY_TAB))
        {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            Project.gluPerspective(cameraFov, (float)(resolution.getScaledWidth_double() / resolution.getScaledHeight_double()), 1F, 10000F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.clearColor(100F, 100F, 100F, 255F);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            modelSelector = new ModelSelector(this, 20);

            ArrayList<me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree> trees = windowModelTree.modelList.trees;

            boolean stop = false;
            for (me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree tree : trees)
            {
                if (tree.selected && tree.attachedObject instanceof CubeInfo)
                {
                    CubeInfo cube = (CubeInfo)tree.attachedObject;

                    modelSelector.fakeRenderSelectedCube(cube);

                    stop = true;
                    break;
                }
            }

            if(!stop)
            {
                modelSelector.fakeRender();
            }

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, -5000.0D, 5000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();

            GlStateManager.pushMatrix();

            //        Block block = Blocks.diamond_ore;

            Minecraft.getMinecraft().renderEngine.bindTexture(txVoxel);

            GlStateManager.translate(width - (levels.get(1).isEmpty() ? 15F : 15F + levels.get(1).get(0).width), height - 15F - windowAnimate.getHeight(), 3000F);
            float scale = 15F;
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            GlStateManager.rotate(-15F + cameraPitch + 180F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-38F + cameraYaw, 0.0F, 1.0F, 0.0F);

            GlStateManager.pushMatrix();
            float scale1 = 16F;
            GlStateManager.scale(scale1, scale1, scale1);

            voxel.render(0.0625F);

            GlStateManager.popMatrix();

            GlStateManager.popMatrix();
        }
    }

    public void updateCube(CubeInfo cube) {
        Gson gson = new Gson();
        String s = gson.toJson(cube);
        if(!this.remoteSession)
        {
            Tabula.proxy.tickHandlerClient.mainframe.updateCube(this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, this.windowAnimate.animList.selectedIdentifier, this.windowAnimate.timeline.selectedIdentifier, this.windowAnimate.timeline.getCurrentPos());
        }
        else if(!this.sessionEnded && this.isEditor)
        {
            Tabula.channel.sendToServer(new PacketGenericMethod(this.host, "updateCube", this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, this.windowAnimate.animList.selectedIdentifier, this.windowAnimate.timeline.selectedIdentifier, this.windowAnimate.timeline.getCurrentPos()));
        }
    }

    private boolean isPasteBlocked() {
        return levels.stream().flatMap(Collection::stream).anyMatch(w -> w instanceof WindowOpenProject || w instanceof WindowLoadTexture);
    }

    public static Matrix4d globalRotationMatrix(CubeInfo info, Function<String, Optional<CubeInfo>> parenter) {
        Matrix4d boxRotateX = new Matrix4d();
        Matrix4d boxRotateY = new Matrix4d();
        Matrix4d boxRotateZ = new Matrix4d();

        boxRotateX.rotX((float) Math.toRadians(info.rotation[0]));
        boxRotateY.rotY((float) Math.toRadians(-info.rotation[1]));
        boxRotateZ.rotZ((float) Math.toRadians(-info.rotation[2]));

        Matrix4d parentGlobal = parenter.apply(info.parentIdentifier).map(c -> globalRotationMatrix(c, parenter)).orElseGet(() -> {
            Matrix4d m = new Matrix4d();
            m.setIdentity();
            return m;
        });

        parentGlobal.mul(boxRotateZ);
        parentGlobal.mul(boxRotateY);
        parentGlobal.mul(boxRotateX);

        return parentGlobal;
    }

    public static void rotatePoint(Point3d point, CubeInfo info, Function<String, Optional<CubeInfo>> parenter) {
        parenter.apply(info.parentIdentifier).ifPresent(c -> rotatePoint(point, c, parenter));

        Matrix4d boxRotateX = new Matrix4d();
        Matrix4d boxRotateY = new Matrix4d();
        Matrix4d boxRotateZ = new Matrix4d();

        boxRotateX.rotX(Math.toRadians(-info.rotation[0]));
        boxRotateY.rotY(Math.toRadians(info.rotation[1]));
        boxRotateZ.rotZ(Math.toRadians(info.rotation[2]));

        boxRotateZ.transform(point);
        boxRotateY.transform(point);
        boxRotateX.transform(point);
    }

    public static Optional<Vec3d> getPosition(Pair<Integer, String> pair, Function<String, Optional<CubeInfo>> parenter) {
        return parenter.apply(pair.getRight()).map(c -> {
            int i = pair.getLeft();
            int x = (i >> 2) & 1;
            int y = (i >> 1) & 1;
            int z = i & 1;
            return getModelPosAlpha(c, x, y, z, parenter);
        });
    }

    @Override
    public void updateKeyStates()
    {
        super.updateKeyStates();

        keyDeleteDown = Keyboard.isKeyDown(Keyboard.KEY_DELETE);
        keyXDown = Keyboard.isKeyDown(Keyboard.KEY_X);
        keyCDown = Keyboard.isKeyDown(Keyboard.KEY_C);
        keyVDown = Keyboard.isKeyDown(Keyboard.KEY_V);
        keyZDown = Keyboard.isKeyDown(Keyboard.KEY_Z);
        keyYDown = Keyboard.isKeyDown(Keyboard.KEY_Y);
        keySDown = Keyboard.isKeyDown(Keyboard.KEY_S);
        keyNDown = Keyboard.isKeyDown(Keyboard.KEY_N);
        keyODown = Keyboard.isKeyDown(Keyboard.KEY_O);
        keyHomeDown = Keyboard.isKeyDown(Keyboard.KEY_HOME);
        keyEndDown = Keyboard.isKeyDown(Keyboard.KEY_END);
        keyTabDown = Keyboard.isKeyDown(Keyboard.KEY_TAB);
    }

    public boolean hasOpenProject()
    {
        return !projectManager.projects.isEmpty();
    }

    public ProjectInfo getOpenProject()
    {
        return projectManager.selectedProject >= 0 ? projectManager.projects.get(projectManager.selectedProject) : null;
    }


    public void applyCamera() {
        float scale = 100F;
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(0F, -1.75F, -9F);
        GlStateManager.scale(cameraZoom, cameraZoom, cameraZoom);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        GlStateManager.translate(cameraOffsetX, cameraOffsetY, 0.0F);
        GlStateManager.rotate(-15F + cameraPitch + 180F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-38F + cameraYaw, 0.0F, 1.0F, 0.0F);
    }

    public void applyModelTranslation() {
        GlStateManager.translate(0.0F, 2.0005F, 0.0F);
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
    }

    public Pair<Integer, String> renderVertexSnapping(boolean searchSelectedVertex, int selectedVertex) {

        int vertex = -1;
        String iden = "";

        GlStateManager.pushMatrix();

        GlStateManager.enableLighting();


        RenderHelper.enableGUIStandardItemLighting();

        int ii = 15728880;
        int jj = ii % 65536;
        int kk = ii / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)jj / 1.0F, (float)kk / 1.0F);

        applyCamera();

        ArrayList<ElementListTree.Tree> trees = windowModelTree.modelList.trees;

        for (ElementListTree.Tree tree : trees) {
            if (tree.selected && tree.attachedObject instanceof CubeInfo) {
                CubeInfo info = (CubeInfo) tree.attachedObject;

                boolean otherCubeSelected = !info.identifier.equals(this.anchorSelectedVertex.getRight());

                GlStateManager.pushMatrix();

                GlStateManager.scale(-1, 1, -1);
                GlStateManager.translate(0, 2, 0);

                IntBuffer buffer = BufferUtils.createIntBuffer(1);

                for (int i = 0; i < 8; i++) {
                    int x = (i >> 2) & 1;
                    int y = (i >> 1) & 1;
                    int z = i & 1;

                    Vec3d alpha = getModelPosAlpha(info, x, y, z, this::getForIdentifier);

                    if(searchSelectedVertex) {
                        buffer.rewind();
                        GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
                        int before = buffer.get();

                        drawCubeoid(alpha.addVector(-0.01, -0.01, -0.01), alpha.addVector(0.01, 0.01, 0.01));

                        buffer.rewind();
                        GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
                        int after = buffer.get();

                        if(before != after) {
                            vertex = i;
                            iden = info.identifier;
                        }
                    } else {
                        if(info.identifier.equals(anchorSelectedVertex.getRight()) && i == this.anchorSelectedVertex.getLeft()) {
                            GlStateManager.color(0, 1F, 0);
                        } else if(i == selectedVertex) {
                            GlStateManager.color(1F, 0, 0);
                        } else {
                            GlStateManager.color(0.5F, 0.25F, 1F);
                        }
                        drawCubeoid(alpha.addVector(-0.01, -0.01, -0.01), alpha.addVector(0.01, 0.01, 0.01));
                        GlStateManager.color(1F, 1F, 1F);

                        if(i == selectedVertex && otherCubeSelected) {
                            getPosition(this.anchorSelectedVertex, this::getForIdentifier).ifPresent(from -> {
                                BufferBuilder buff = Tessellator.getInstance().getBuffer();
                                buff.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

                                buff.pos(from.x, from.y, from.z).color(1F, 1F, 1F, 1F).endVertex();
                                buff.pos(alpha.x, alpha.y, alpha.z).color(1F, 1F, 1F, 1F).endVertex();

                                Tessellator.getInstance().draw();
                            });
                        }
                    }

                }

                GlStateManager.popMatrix();

                break;
            } else if(tree.attachedObject instanceof CubeInfo && this.anchorSelectedVertex.getRight().equals(((CubeInfo)tree.attachedObject).identifier)) {
                CubeInfo info = (CubeInfo) tree.attachedObject;

                int i = this.anchorSelectedVertex.getLeft();

                int x = (i >> 2) & 1;
                int y = (i >> 1) & 1;
                int z = i & 1;

                Vec3d alpha = getModelPosAlpha(info, x, y, z, this::getForIdentifier);

                GlStateManager.pushMatrix();

                GlStateManager.scale(-1, 1, -1);
                GlStateManager.translate(0, 2, 0);

                GlStateManager.color(1F, 0.5F, 0.25F);
                drawCubeoid(alpha.addVector(-0.01, -0.01, -0.01), alpha.addVector(0.01, 0.01, 0.01));

                GlStateManager.popMatrix();
            }
        }

        GlStateManager.popMatrix();

        return Pair.of(vertex, iden);
    }

    private Optional<CubeInfo> getForIdentifier(String identifier) {
        ArrayList<ElementListTree.Tree> trees = windowModelTree.modelList.trees;

        for (ElementListTree.Tree tree : trees) {
            if (tree.attachedObject instanceof CubeInfo) {
                CubeInfo info = (CubeInfo) tree.attachedObject;
                if(info.identifier.equals(identifier)) {
                    return Optional.of(info);
                }
            }
        }

        return Optional.empty();

    }

    //Copied From DumbLibrary -> https://github.com/Dumb-Code/DumbLibrary/blob/5cd5fe4e5f94385d59de928168642b86cf9b7a0a/src/main/java/net/dumbcode/dumblibrary/server/animation/TabulaUtils.java#L103-L140
    public static Vec3d getModelPosAlpha(CubeInfo cube, float xalpha, float yalpha, float zalpha, Function<String, Optional<CubeInfo>> parenter) {
        double[] offset = cube.offset;
        int[] dimensions = cube.dimensions;
        double[] scale = cube.scale;

        double grow = cube.mcScale;

        return getModelPos(cube,
            (offset[0] + ((dimensions[0] + grow*2) * xalpha - grow) * scale[0]) / 16D,
            (offset[1] + ((dimensions[1] + grow*2) * yalpha - grow) * scale[1]) / -16D,
            (offset[2] + ((dimensions[2] + grow*2) * zalpha - grow) * scale[2]) / -16D,
            parenter
        );
    }

    //Copied From DumbLibrary -> https://github.com/Dumb-Code/DumbLibrary/blob/5cd5fe4e5f94385d59de928168642b86cf9b7a0a/src/main/java/net/dumbcode/dumblibrary/server/animation/TabulaUtils.java#L103-L140
    public static Vec3d getModelPos(CubeInfo cube, double x, double y, double z, Function<String, Optional<CubeInfo>> parenter) {
        Point3d rendererPos = new Point3d(x, y, z);

        Matrix4d boxTranslate = new Matrix4d();
        Matrix4d boxRotateX = new Matrix4d();
        Matrix4d boxRotateY = new Matrix4d();
        Matrix4d boxRotateZ = new Matrix4d();

        double[] point = cube.position;
        boxTranslate.set(new Vector3d(point[0] / 16D, -point[1] / 16D, -point[2] / 16D));

        double[] rotation = cube.rotation;

        boxRotateX.rotX(Math.toRadians(rotation[0]));
        boxRotateY.rotY(Math.toRadians(-rotation[1]));
        boxRotateZ.rotZ(Math.toRadians(-rotation[2]));

        boxRotateX.transform(rendererPos);
        boxRotateY.transform(rendererPos);
        boxRotateZ.transform(rendererPos);
        boxTranslate.transform(rendererPos);

        return parenter.apply(cube.parentIdentifier)
            .map(c -> getModelPos(c, rendererPos.getX(), rendererPos.getY(), rendererPos.getZ(), parenter))
            .orElseGet(() -> new Vec3d(rendererPos.getX(), rendererPos.getY(), rendererPos.getZ()));
    }

    //Copied from ProjectNublar -> https://github.com/Dumb-Code/ProjectNublar/blob/dd8e5efeb4fab2659d7b9845d7d962d351f153d2/src/main/java/net/dumbcode/projectnublar/client/utils/RenderUtils.java#L75-L103
    public static void drawCubeoid(Vec3d s, Vec3d e) {
        BufferBuilder buff = Tessellator.getInstance().getBuffer();
        buff.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_NORMAL);
        buff.pos(s.x, e.y, s.z).normal(0, 1, 0).endVertex();
        buff.pos(s.x, e.y, e.z).normal(0, 1, 0).endVertex();
        buff.pos(e.x, e.y, e.z).normal(0, 1, 0).endVertex();
        buff.pos(e.x, e.y, s.z).normal(0, 1, 0).endVertex();
        buff.pos(s.x, s.y, e.z).normal(0, -1, 0).endVertex();
        buff.pos(s.x, s.y, s.z).normal(0, -1, 0).endVertex();
        buff.pos(e.x, s.y, s.z).normal(0, -1, 0).endVertex();
        buff.pos(e.x, s.y, e.z).normal(0, -1, 0).endVertex();
        buff.pos(e.x, e.y, e.z).normal(1, 0, 0).endVertex();
        buff.pos(e.x, s.y, e.z).normal(1, 0, 0).endVertex();
        buff.pos(e.x, s.y, s.z).normal(1, 0, 0).endVertex();
        buff.pos(e.x, e.y, s.z).normal(1, 0, 0).endVertex();
        buff.pos(s.x, s.y, e.z).normal(-1, 0, 0).endVertex();
        buff.pos(s.x, e.y, e.z).normal(-1, 0, 0).endVertex();
        buff.pos(s.x, e.y, s.z).normal(-1, 0, 0).endVertex();
        buff.pos(s.x, s.y, s.z).normal(-1, 0, 0).endVertex();
        buff.pos(s.x, e.y, e.z).normal(0, 0, 1).endVertex();
        buff.pos(s.x, s.y, e.z).normal(0, 0, 1).endVertex();
        buff.pos(e.x, s.y, e.z).normal(0, 0, 1).endVertex();
        buff.pos(e.x, e.y, e.z).normal(0, 0, 1).endVertex();
        buff.pos(s.x, s.y, s.z).normal(0, 0, -1).endVertex();
        buff.pos(s.x, e.y, s.z).normal(0, 0, -1).endVertex();
        buff.pos(e.x, e.y, s.z).normal(0, 0, -1).endVertex();
        buff.pos(e.x, s.y, s.z).normal(0, 0, -1).endVertex();
        Tessellator.getInstance().draw();
    }

    public void renderWorkspace(int mouseX, int mouseY, float f)
    {
        if(cameraZoom < 0.05F)
        {
            cameraZoom = 0.05F;
        }
        else if(cameraZoom > 15F)
        {
            cameraZoom = 15F;
        }

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.pushMatrix();

        GlStateManager.enableLighting();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        int light1 = 15728880 >> 16 & 65535;
        int light2 = 15728880 & 65535;

        RenderHelper.enableGUIStandardItemLighting();

        int ii = 15728880;
        int jj = ii % 65536;
        int kk = ii / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)jj / 1.0F, (float)kk / 1.0F);

        applyCamera();

        Block block = Block.getBlockFromName(((Theme)currentTheme).workspaceBlock.block);
        int meta = ((Theme)currentTheme).workspaceBlock.metadata;

        if(block == null)
        {
            block = Blocks.PLANKS;
        }

        boolean enableWood = false;

        for(int i = 0; i < levels.get(3).get(0).elements.size(); i++)
        {
            if(levels.get(3).get(0).elements.get(i).id == WindowTopDock.ID_WOOD)
            {
                ElementToggle toggle = (ElementToggle)levels.get(3).get(0).elements.get(i);
                enableWood = toggle.toggledState;
            }
        }

        if(enableWood)
        {
            ItemStack spoof = new ItemStack(block, 1, meta);
            IBlockState state = block.getStateFromMeta(meta);
            RendererHelper.renderBakedModel(mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state), -1, spoof);
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if(projectManager.selectedProject != -1)
        {
            GlStateManager.pushMatrix();
            GlStateManager.disableCull();
            applyModelTranslation();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);

            ArrayList<CubeInfo> selected = new ArrayList<>();
            if(!this.vertexSnapping)
            for(me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree tree : windowModelTree.modelList.trees)
            {
                if(tree.selected)
                {
                    if(tree.attachedObject instanceof CubeGroup)
                    {
                        addElementsForSelection((CubeGroup)tree.attachedObject, selected);
                    }
                    if(tree.attachedObject instanceof CubeInfo)
                    {
                        selected.add((CubeInfo)tree.attachedObject);
                    }
                }
            }
            ArrayList<CubeInfo> hidden = getHiddenElements();

            boolean renderRotationPoint = Tabula.config.renderRotationPoint == 1;

            applyModelAnimations();

            GlStateManager.scale(1D / info.scale[0], 1D / info.scale[1], 1D / info.scale[2]);
            GlStateManager.enableLighting();
            if(windowTexture.imageId != -1)
            {
                GlStateManager.bindTexture(windowTexture.imageId);

                info.model.render(0.0625F, selected, hidden, cameraZoom, true, 0, renderRotationPoint, Tabula.config.renderModelControls == 1);
                info.model.render(0.0625F, selected, hidden, cameraZoom, true, 1, renderRotationPoint, Tabula.config.renderModelControls == 1);
            }
            else
            {
                GlStateManager.disableTexture2D();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                info.model.render(0.0625F, selected, hidden, cameraZoom, false, 0, renderRotationPoint, Tabula.config.renderModelControls == 1);
                info.model.render(0.0625F, selected, hidden, cameraZoom, false, 1, renderRotationPoint, Tabula.config.renderModelControls == 1);

                GlStateManager.enableTexture2D();
            }

            if(info.ghostModel != null)
            {
                if(info.ghostModel.model == null)
                {
                    info.ghostModel.initClient();

                    if(info.ghostModel.bufferedTexture != null)
                    {
                        info.ghostModel.bufferedTextureId = TextureUtil.uploadTextureImage(TextureUtil.glGenTextures(), info.ghostModel.bufferedTexture);
                    }
                }

                GlStateManager.pushMatrix();

                GlStateManager.scale(1D / info.ghostModel.scale[0], 1D / info.ghostModel.scale[1], 1D / info.ghostModel.scale[2]);

                ArrayList<CubeInfo> emptyList = new ArrayList<>();

                if(info.ghostModel.bufferedTextureId != -1)
                {
                    GlStateManager.bindTexture(info.ghostModel.bufferedTextureId);

                    info.ghostModel.model.render(0.0625F, emptyList, emptyList, cameraZoom, true, 0, false, false);
                    info.ghostModel.model.render(0.0625F, emptyList, emptyList, cameraZoom, true, 1, false, false);
                }
                else
                {
                    GlStateManager.disableTexture2D();
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                    info.ghostModel.model.render(0.0625F, emptyList, emptyList, cameraZoom, false, 0, false, false);
                    info.ghostModel.model.render(0.0625F, emptyList, emptyList, cameraZoom, false, 1, false, false);

                    GlStateManager.enableTexture2D();
                }

                GlStateManager.popMatrix();
            }

            GlStateManager.enableCull();
            GlStateManager.popMatrix();

            resetModelAnimations();
        }

        if(Tabula.config.renderGrid == 1)
        {
            Minecraft.getMinecraft().getTextureManager().bindTexture(grid16);
            double dist = 0.125D;
            double pX = -3.495D - dist;
            double pY = 0.500125D;
            double pZ = -3.495D - dist;
            double w = 7 + (dist * 2);
            double l = 7 + (dist * 2);
            bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);
            bufferbuilder.pos(pX, pY, pZ + l).tex(-0.125D, 7.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX + w, pY, pZ + l).tex(7.125D, 7.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX + w, pY, pZ).tex(7.125D, -0.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX, pY, pZ).tex(-0.125D, -0.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX + w, pY, pZ + l).tex(7.125D, 7.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX, pY, pZ + l).tex(-0.125D, 7.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX, pY, pZ).tex(-0.125D, -0.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            bufferbuilder.pos(pX + w, pY, pZ).tex(7.125D, -0.125D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
            tessellator.draw();
        }

        GlStateManager.popMatrix();

        ScaledResolution resolution = new ScaledResolution(mc);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, -5000.0D, 5000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        GlStateManager.pushMatrix();

        block = Blocks.FURNACE;
        //        Block block = Blocks.diamond_ore;

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.translate(width - (levels.get(1).isEmpty() ? 15F : 15F + levels.get(1).get(0).width), height - 15F - windowAnimate.getHeight(), 3000F);
        float scale = 15F;
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.scale(-1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(-15F + cameraPitch + 180F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-38F + cameraYaw, 0.0F, 1.0F, 0.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        mc.getRenderItem().renderItem(new ItemStack(block, 1), ItemCameraTransforms.TransformType.FIXED);
        GlStateManager.popMatrix();

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Minecraft.getMinecraft().getTextureManager().bindTexture(orientationBase);
        double dist = 0.125D;
        double pX = -1D - dist;
        double pY = -0.500125D;
        double pZ = -1D - dist;
        double w = 2 + (dist * 2);
        double l = 2 + (dist * 2);
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);
        bufferbuilder.pos(pX, pY, pZ + l).tex(0.0D, 1.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX + w, pY, pZ + l).tex(1.0D, 1.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX + w, pY, pZ).tex(1.0D, 0.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX	  , pY, pZ).tex(0.0D, 0.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX + w, pY, pZ + l).tex(1.0D, 1.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX	  , pY, pZ + l).tex(0.0D, 1.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX	  , pY, pZ).tex(0.0D, 0.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        bufferbuilder.pos(pX + w, pY, pZ).tex(1.0D, 0.0D).lightmap(light1, light2).color(1.0F, 1.0F, 1.0F, 0.5F).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();

        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableLighting();

        GlStateManager.popMatrix();

    }

    //only group parts. not actual individual cubes
    public ArrayList<CubeInfo> getHiddenElements() {
        ArrayList<CubeInfo> hidden = new ArrayList<>();
        for(me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree tree : windowModelTree.modelList.trees)
        {
            if(tree.attachedObject instanceof CubeGroup)
            {
                addElementsForHiding((CubeGroup)tree.attachedObject, hidden);
            }
        }

        return hidden;
    }

    private static void addElementsForSelection(CubeGroup group, ArrayList<CubeInfo> selected)
    {
        for(CubeGroup group1 : group.cubeGroups)
        {
            addElementsForSelection(group1, selected);
        }
        for(CubeInfo info : group.cubes)
        {
            addElementsForSelection(info, selected);
        }
    }

    public static void addElementsForHiding(CubeGroup group, ArrayList<CubeInfo> selected)
    {
        if(group.hidden)
        {
            for(CubeGroup group1 : group.cubeGroups)
            {
                addElementsForSelection(group1, selected);
            }
            for(CubeInfo info : group.cubes)
            {
                addElementsForSelection(info, selected);
            }
        }
        for(CubeGroup group1 : group.cubeGroups)
        {
            addElementsForHiding(group1, selected);
        }
    }

    private static void addElementsForSelection(CubeInfo cube, ArrayList<CubeInfo> selected)
    {
        selected.add(cube);
        for(CubeInfo child : cube.getChildren())
        {
            addElementsForSelection(child, selected);
        }
    }

    public void cut()
    {
        windowControls.selectedObject = null;
        windowControls.refresh = true;

        copy();

        for(Element e : windowModelTree.elements)
        {
            if(e.id == 2)
            {
                windowModelTree.elementTriggered(e);
            }
        }
    }

    public void copy()
    {
        for(me.ichun.mods.tabula.client.gui.window.element.ElementListTree.Tree tree : windowModelTree.modelList.trees)
        {
            if(tree.selected)
            {
                if(tree.attachedObject instanceof CubeInfo || tree.attachedObject instanceof CubeGroup)
                {
                    cubeCopied = tree.attachedObject;
                }
                break;
            }
        }
    }

    public void paste(boolean inPlace, boolean withChildren)
    {
        if(cubeCopied instanceof CubeInfo)
        {
            Gson gson = new Gson();
            String s = gson.toJson(cubeCopied);
            if(!remoteSession)
            {
                Tabula.proxy.tickHandlerClient.mainframe.createNewCube(this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, inPlace, withChildren);
            }
            else if(!sessionEnded && isEditor)
            {
                Tabula.channel.sendToServer(new PacketGenericMethod(host, "createNewCube", this.projectManager.projects.get(this.projectManager.selectedProject).identifier, s, inPlace, withChildren));
            }
        }
        else if(cubeCopied instanceof CubeGroup)
        {
            if(!remoteSession)
            {
                Tabula.proxy.tickHandlerClient.mainframe.copyGroupTo(this.projectManager.projects.get(this.projectManager.selectedProject).identifier, ((CubeGroup)cubeCopied).identifier, inPlace);
            }
            else
            {
                Tabula.channel.sendToServer(new PacketGenericMethod(host, "copyGroupTo", this.projectManager.projects.get(this.projectManager.selectedProject).identifier, ((CubeGroup)cubeCopied).identifier, inPlace));
            }
        }
    }

    public void save(boolean close)
    {
        if(!this.projectManager.projects.isEmpty())
        {
            ProjectInfo proj = this.projectManager.projects.get(this.projectManager.selectedProject);
            boolean saveAs = true;
            boolean error = false;

            if(proj.saveFile != null && proj.saveFile.exists())
            {
                String md5 = IOUtil.getMD5Checksum(proj.saveFile);
                if(md5 != null && md5.equals(proj.saveFileMd5))
                {
                    if(ProjectInfo.saveProject(proj, proj.saveFile))
                    {
                        proj.saveFileMd5 = IOUtil.getMD5Checksum(proj.saveFile);
                        saveAs = false;
                        if(close)
                        {
                            closeProject(proj);
                        }
                    }
                    else
                    {
                        error = true;
                    }
                }
            }
            if(saveAs)
            {
                this.addWindowOnTop(new WindowSaveAs(this, this.width / 2 - 100, this.height / 2 - 80, 200, 100, 200, 100, close).putInMiddleOfScreen());
            }
            if(error)
            {
                this.addWindowOnTop(new WindowPopup(this, 0, 0, 180, 80, 180, 80, "window.saveAs.failed").putInMiddleOfScreen());
            }
        }
    }

    public void switchState(boolean undo)
    {
        if(!projectManager.projects.isEmpty())
        {
            if(!remoteSession)
            {
                Tabula.proxy.tickHandlerClient.mainframe.switchState(projectManager.projects.get(projectManager.selectedProject).identifier, undo);
            }
            else if(!sessionEnded && isEditor)
            {
                Tabula.channel.sendToServer(new PacketGenericMethod(host, "switchState", projectManager.projects.get(projectManager.selectedProject).identifier, undo));
            }
        }
    }

    public void closeProject(ProjectInfo project)
    {
        if(!project.saved)
        {
            projectManager.changeProject(project);
            addWindowOnTop(new WindowSaveBeforeClosing(this, projectManager.projects.get(projectManager.selectedProject)).putInMiddleOfScreen());
        }
        else
        {
            Tabula.proxy.tickHandlerClient.mainframe.closeProject(project.identifier);
        }
    }

    public void applyModelAnimations()
    {
        if(projectManager.selectedProject != -1)
        {
            ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);

            ArrayList<CubeInfo> allCubes = info.getAllCubes();

            for(Animation anim : info.anims)
            {
                if(anim.identifier.equalsIgnoreCase(windowAnimate.animList.selectedIdentifier))
                {
                    for(Map.Entry<String, ArrayList<AnimationComponent>> e : anim.sets.entrySet())
                    {
                        for(CubeInfo cube : allCubes)
                        {
                            if(cube.identifier.equals(e.getKey()))
                            {
                                ArrayList<AnimationComponent> components = e.getValue();
                                Collections.sort(components);

                                for(AnimationComponent comp : components)
                                {
                                    if(!comp.hidden)
                                    {
                                        comp.animate(cube, anim.playTime + (anim.playing ? renderTick : 0));
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    public void resetModelAnimations()
    {
        if(projectManager.selectedProject != -1)
        {
            ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);

            ArrayList<CubeInfo> allCubes = info.getAllCubes();

            for(Animation anim : info.anims)
            {
                if(anim.identifier.equalsIgnoreCase(windowAnimate.animList.selectedIdentifier))
                {
                    for(Map.Entry<String, ArrayList<AnimationComponent>> e : anim.sets.entrySet())
                    {
                        for(CubeInfo cube : allCubes)
                        {
                            if(cube.identifier.equals(e.getKey()))
                            {
                                ArrayList<AnimationComponent> components = e.getValue();
                                Collections.sort(components);

                                for(AnimationComponent comp : components)
                                {
                                    if(!comp.hidden)
                                    {
                                        comp.reset(cube, anim.playTime + (anim.playing ? renderTick : 0));
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void handleMouseInput(){} //Mouse handling is done in drawScreen

    @Override
    protected void keyTyped(char c, int key)
    {
        if (key == 1)
        {
            boolean wantedToExit = wantToExit;
            wantToExit = true;
            if(wantedToExit)
            {
                for(int i = levels.size() - 1; i >= 0 ; i--)
                {
                    for(int j = levels.get(i).size() - 1; j >= 0; j--)
                    {
                        Window window = levels.get(i).get(j);
                        if(window instanceof WindowSaveBeforeClosing)
                        {
                            wantToExit = false;
                            removeWindow(window);
                            break;
                        }
                    }
                }
            }
        }
        else if(elementSelected != null)
        {
            elementSelected.keyInput(c, key);
        }
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);

        for(int i = levels.size() - 1; i >= 0; i--)
        {
            for(int j = levels.get(i).size() - 1; j >= 0; j--)
            {
                levels.get(i).get(j).shutdown();
            }
        }

        Minecraft.getMinecraft().gameSettings.guiScale = oriScale;
        if(!remoteSession)
        {
            if(host != null && !sessionEnded)
            {
                Tabula.channel.sendToServer(new PacketEndSession(host, hostX, hostY, hostZ, false));
            }
            if(init)
            {
                Tabula.proxy.tickHandlerClient.mainframe.shutdown();
            }
        }
        else
        {
            Tabula.channel.sendToServer(new PacketRemoveListener(host, Minecraft.getMinecraft().getSession().getUsername()));
        }
        if(host != null)
        {
            StringBuilder sb = new StringBuilder();

            sb.append(windowChat.posX);
            sb.append(":");
            sb.append(windowChat.posY);
            sb.append(":");
            sb.append(windowChat.width);
            sb.append(":");
            sb.append(windowChat.height);
            sb.append(":");
            sb.append(windowChat.docked);
            sb.append(":");
            sb.append(windowChat.minimized);
            sb.append(":");
            sb.append(windowChat.oriWidth);
            sb.append(":");
            sb.append(windowChat.oriHeight);

            Tabula.config.chatWindow = (sb.toString());
            Tabula.config.save();
        }

        voxel.destroy();
    }

    public void layoutTextures() {
        if(projectManager.selectedProject == -1) return;
        ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);
        if (!layoutTextures(info.textureWidth, info.textureHeight)) {
            this.addWindowOnTop(new WindowPopup(this, 0, 0, 180, 80, 180, 80, "window.autoLayout.failed").putInMiddleOfScreen());
        }
    }

    public void minimizeTextureToSquare() {
        if(projectManager.selectedProject == -1) return;
        ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);
        //A max texture format size of 1024
        for (int size = 1; size < 64; size++) {
            if(this.layoutTextures(size * 16, size * 16)) {
                String projName = info.modelName;
                String authName = info.authorName;
                int dimW = size * 16;
                int dimH = size * 16;
                double scaleX = info.scale[0];
                double scaleY = info.scale[1];
                double scaleZ = info.scale[2];

                if(!remoteSession)
                {
                    Tabula.proxy.tickHandlerClient.mainframe.editProject(projectManager.projects.get(projectManager.selectedProject).identifier, projName, authName, dimW, dimH, scaleX, scaleY, scaleZ);
                }
                else if(!sessionEnded && isEditor)
                {
                    Tabula.channel.sendToServer(new PacketGenericMethod(host, "editProject", projectManager.projects.get(projectManager.selectedProject).identifier, projName, authName, dimW, dimH, scaleX, scaleY, scaleZ));
                }
                
                break;
            }
        }
    }

    public boolean layoutTextures(int texWidth, int texHeight) {
        Map<String, int[]> newCoords = new HashMap<>();

        ProjectInfo info = projectManager.projects.get(projectManager.selectedProject);
        boolean[][] positions = new boolean[texWidth][texHeight];
        ArrayList<CubeInfo> cubes = info.getAllCubes();

        for(CubeInfo cube : cubes) {
            boolean collide = true;
            int breakout = 0;
            int newU = 0;
            int newV = 0;

            while (collide && newU + cube.dimensions[2] * 2 + cube.dimensions[0] * 2 < texWidth && newV + cube.dimensions[1] + cube.dimensions[2] < texHeight && breakout++ < 100000) {
                collide = false;
                for (int i = 0; i < cube.dimensions[0]; i++) {
                    for (int j = 0; j < cube.dimensions[1]; j++) {
                        for (int k = 0; k < cube.dimensions[2]; k++) {
                            if (withinBounds(texWidth, texHeight, newU + k, newV + cube.dimensions[2] + j) && positions[newU + k][newV + cube.dimensions[2] + j]) {
                                collide = true;
                            }
                            if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + i, newV + cube.dimensions[2] + j) && positions[newU + cube.dimensions[2] + i][newV + cube.dimensions[2] + j]) {
                                collide = true;
                            }
                            if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + k, newV + cube.dimensions[2] + j) && positions[newU + cube.dimensions[2] + cube.dimensions[0] + k][newV + cube.dimensions[2] + j]) {
                                collide = true;
                            }
                            if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + cube.dimensions[2] + i, newV + cube.dimensions[2] + j) && positions[newU + cube.dimensions[2] + cube.dimensions[0] + cube.dimensions[2] + i][newV + cube.dimensions[2] + j]) {
                                collide = true;
                            }
                            if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + i, newV + k) && positions[newU + cube.dimensions[2] + i][newV + k]) {
                                collide = true;
                            }
                            if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + i, newV + k) && positions[newU + cube.dimensions[2] + cube.dimensions[0] + i][newV + k]) {
                                collide = true;
                            }
                        }
                    }
                }

                if (!collide) {
                    for (int i = 0; i < cube.dimensions[0]; i++) {
                        for (int j = 0; j < cube.dimensions[1]; j++) {
                            for (int k = 0; k < cube.dimensions[2]; k++) {
                                if (withinBounds(texWidth, texHeight, newU + k, newV + cube.dimensions[2] + j)) {
                                    positions[newU + k][newV + cube.dimensions[2] + j] = true;
                                }
                                if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + i, newV + cube.dimensions[2] + j)) {
                                    positions[newU + cube.dimensions[2] + i][newV + cube.dimensions[2] + j] = true;
                                }
                                if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + k, newV + cube.dimensions[2] + j)) {
                                    positions[newU + cube.dimensions[2] + cube.dimensions[0] + k][newV + cube.dimensions[2] + j] = true;
                                }
                                if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + cube.dimensions[2] + i, newV + cube.dimensions[2] + j)) {
                                    positions[newU + cube.dimensions[2] + cube.dimensions[0] + cube.dimensions[2] + i][newV + cube.dimensions[2] + j] = true;
                                }
                                if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + i, newV + k)) {
                                    positions[newU + cube.dimensions[2] + i][newV + k] = true;
                                }
                                if (withinBounds(texWidth, texHeight, newU + cube.dimensions[2] + cube.dimensions[0] + i, newV + k)) {
                                    positions[newU + cube.dimensions[2] + cube.dimensions[0] + i][newV + k] = true;
                                }
                            }
                        }
                    }

                }
                if(collide) {
                    newU++;
                    if(newU + cube.dimensions[2] * 2 + cube.dimensions[0] * 2 >= texWidth) {
                        newU = 0;
                        newV++;
                    }
                }
            }

            if(breakout >= 100000 || collide) {
                return false;
            } else {
                newCoords.put(cube.identifier, new int[]{ newU, newV });
            }
        }

        BufferedImage oldImg = info.bufferedTexture;

        int scale = oldImg == null ? 0 : oldImg.getWidth() / info.textureWidth;

        BufferedImage newImg = oldImg == null ? null : new BufferedImage(texWidth*scale, texHeight*scale, oldImg.getType());

        for (CubeInfo cube : info.getAllCubes()) {
            int[] dim = cube.dimensions;
            int[] uv = newCoords.get(cube.identifier);

            if(oldImg != null) {
                int[] top = oldImg.getRGB((cube.txOffset[0] + dim[2])*scale, cube.txOffset[1]*scale, 2*dim[0]*scale, dim[2]*scale, null, 0, oldImg.getWidth());
                newImg.setRGB((uv[0] + dim[2])*scale, uv[1]*scale, 2*dim[0]*scale, dim[2]*scale, top, 0, oldImg.getWidth());

                int[] bottom = oldImg.getRGB(cube.txOffset[0]*scale, (cube.txOffset[1] + dim[2])*scale, 2*(dim[0] + dim[2])*scale, dim[1]*scale, null, 0, oldImg.getWidth());
                newImg.setRGB(uv[0]*scale, (uv[1] + dim[2])*scale, 2*(dim[0] + dim[2])*scale, dim[1]*scale, bottom, 0, oldImg.getWidth());
            }

            cube.txOffset[0] = uv[0];
            cube.txOffset[1] = uv[1];
            this.updateCube(cube);
        }

        if(newImg != null) {
            File newFile = new File(info.textureFile.getParentFile(), info.textureFile.getName().substring(0, info.textureFile.getName().lastIndexOf('.')) + "_sorted.png");

            try {
                ImageIO.write(newImg, "PNG", newFile);

                info.textureFile = newFile;
                info.ignoreNextImage = true;
                info.textureFileMd5 = IOUtil.getMD5Checksum(info.textureFile);
                windowTexture.listenTime = 0;
                Tabula.proxy.tickHandlerClient.mainframe.loadTexture(info.identifier, newImg, false);

            } catch (IOException e) {
                Tabula.LOGGER.error(e);
            }
        }
        return true;
    }

    public boolean withinBounds(int textureWidth, int textureHeight, int x, int y)
    {
        return x >= 0 && x < textureWidth && y >= 0 && y < textureHeight;
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }
}
