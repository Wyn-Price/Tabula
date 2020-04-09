package me.ichun.mods.tabula.client.gui.window;

import me.ichun.mods.ichunutil.client.gui.window.IWorkspace;
import me.ichun.mods.ichunutil.client.gui.window.Window;
import me.ichun.mods.ichunutil.client.gui.window.element.Element;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementButtonTextured;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementListTree;
import me.ichun.mods.ichunutil.client.gui.window.element.ElementToggle;
import me.ichun.mods.ichunutil.client.render.RendererHelper;
import me.ichun.mods.ichunutil.common.core.util.IOUtil;
import me.ichun.mods.ichunutil.common.module.tabula.project.ProjectInfo;
import me.ichun.mods.ichunutil.common.module.tabula.project.components.CubeInfo;
import me.ichun.mods.tabula.client.gui.GuiWorkspace;
import me.ichun.mods.tabula.client.gui.Theme;
import me.ichun.mods.tabula.client.mainframe.core.ProjectHelper;
import me.ichun.mods.tabula.common.Tabula;
import me.ichun.mods.tabula.common.packet.PacketClearTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WindowTexture extends Window
{
    public int listenTime;

    public BufferedImage image;
    public int imageId = -1;

    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private boolean lastMouseDown;
    private String startingMouseDown = "";
    private Set<String> selectedIdentifiers = new HashSet<>();

    public WindowTexture(IWorkspace parent, int x, int y, int w, int h, int minW, int minH)
    {
        super(parent, x, y, w, h, minW, minH, "window.texture.title", true);

        elements.add(new ElementToggle(this, width - BORDER_SIZE - 100, height - BORDER_SIZE - 20, 60, 20, 0, false, 1, 1, "window.texture.listenTexture", "window.texture.listenTextureFull", true));
        elements.add(new ElementButtonTextured(this, width - BORDER_SIZE - 40, height - BORDER_SIZE - 20, 1, false, 1, 1, "window.texture.loadTexture", new ResourceLocation("tabula", "textures/icon/newtexture.png")));
        elements.add(new ElementButtonTextured(this, width - BORDER_SIZE - 20, height - BORDER_SIZE - 20, 2, false, 1, 1, "window.texture.clearTexture", new ResourceLocation("tabula", "textures/icon/cleartexture.png")));
    }

    @Override
    public void draw(int mouseX, int mouseY) //4 pixel border?
    {
        super.draw(mouseX, mouseY);



        if(!((GuiWorkspace)workspace).projectManager.projects.isEmpty() && !minimized)
        {
            ProjectInfo project = ((GuiWorkspace)workspace).projectManager.projects.get(((GuiWorkspace)workspace).projectManager.selectedProject);
            double w = width - (BORDER_SIZE * 2);
            double h = height - BORDER_SIZE - 22 - 15 - 12;
            double rW = w / (double)project.textureWidth;
            double rH = h / (double)project.textureHeight;

            double max = Math.min(rW, rH);
            double offX = (w - (project.textureWidth * max)) / 2D;
            double offY = (h - (project.textureHeight * max)) / 2D;

            double pX = posX + BORDER_SIZE + offX;
            double pY = posY + offY + 15;
            double w1 = (project.textureWidth * max);
            double h1 = (project.textureHeight * max);

            double ratio = (project.textureWidth / w1);

            int gMx = mouseX + posX;
            int gMy = mouseY + posY;

            double diffX = (mouseX - lastMouseX) * ratio;
            double diffY = (mouseY - lastMouseY) * ratio;

            RendererHelper.drawColourOnScreen(200, 200, 200, 255, pX, pY, w1, h1, 0D);

            if(image != null)
            {
                GlStateManager.bindTexture(imageId);
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder bufferbuilder = tessellator.getBuffer();
                bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
                bufferbuilder.pos(pX		, pY + h1	, 0).tex(0.0D, 1.0D).endVertex();
                bufferbuilder.pos(pX + w1, pY + h1	, 0).tex(1.0D, 1.0D).endVertex();
                bufferbuilder.pos(pX + w1, pY			, 0).tex(1.0D, 0.0D).endVertex();
                bufferbuilder.pos(pX		, pY			, 0).tex(0.0D, 0.0D).endVertex();
                tessellator.draw();
            }

            RendererHelper.endGlScissor();
            RendererHelper.startGlScissor((int)pX, (int)pY, (int)w1, (int)h1);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.00625F);

            boolean currentMouse = Mouse.isButtonDown(0);
            if(!currentMouse) {
                startingMouseDown = "";
            }
            boolean mouseDown = !currentMouse && lastMouseDown;
            boolean mouseOverAny = false;

            List<CubeInfo> toUpdate = new ArrayList<>();

            for(ElementListTree.Tree tree : ((GuiWorkspace)workspace).windowModelTree.modelList.trees)
            {
                if(tree.attachedObject instanceof CubeInfo)
                {
                    CubeInfo info = (CubeInfo)tree.attachedObject;
                    int alpha = tree.selected ? 125 : 25;

                    float rMod = 1F;
                    float gMod = 1F;
                    float bMod = 1F;

                    if(selectedIdentifiers.contains(info.identifier)) {
                        rMod = 1F;
                        gMod = 0.5F;
                        bMod = 0.6F;
                        alpha = 125;
                        if(currentMouse) {
                            info.txOffset[0] += diffX;
                            info.txOffset[1] += diffY;
                            toUpdate.add(info);
                        }
                    }

                    double[] posXs = new double[] {
                        pX+info.txOffset[0] / ratio,
                        pX+info.txOffset[0] / ratio + info.dimensions[2] / ratio,
                        pX+info.txOffset[0] / ratio + info.dimensions[2] / ratio + info.dimensions[0] / ratio,
                        pX+info.txOffset[0] / ratio + info.dimensions[2] / ratio + info.dimensions[0] / ratio  + info.dimensions[2] / ratio,
                        pX+info.txOffset[0] / ratio + info.dimensions[2] / ratio,
                        pX+info.txOffset[0] / ratio + info.dimensions[2] / ratio + info.dimensions[0] / ratio
                    };

                    double[] posYs = new double[] {
                        pY+info.txOffset[1] / ratio + info.dimensions[2] / ratio,
                        pY+info.txOffset[1] / ratio + info.dimensions[2] / ratio,
                        pY+info.txOffset[1] / ratio + info.dimensions[2] / ratio,
                        pY+info.txOffset[1] / ratio + info.dimensions[2] / ratio,
                        pY+info.txOffset[1] / ratio,
                        pY+info.txOffset[1] / ratio
                    };

                    double[] widths = new double[] {
                        info.dimensions[2] / ratio,
                        info.dimensions[0] / ratio,
                        info.dimensions[2] / ratio,
                        info.dimensions[0] / ratio,
                        info.dimensions[0] / ratio,
                        info.dimensions[0] / ratio
                    };

                    double[] heights = new double[] {
                        info.dimensions[1] / ratio,
                        info.dimensions[1] / ratio,
                        info.dimensions[1] / ratio,
                        info.dimensions[1] / ratio,
                        info.dimensions[2] / ratio,
                        info.dimensions[2] / ratio
                    };

                    for (int i = 0; i < 6; i++) {
                        if(gMx >= posXs[i] && gMx <= posXs[i] + widths[i] && gMy >= posYs[i] && gMy <= posYs[i] + heights[i]) {
                            mouseOverAny = true;
                            alpha = 125;
                            rMod = 0.5F;
                            bMod = 1F;
                            gMod = 0.25F;

                            if(currentMouse && (diffX != 0 || diffY != 0) && info.identifier.equals(startingMouseDown)) {
                                selectedIdentifiers.add(info.identifier);
                            }

                            if(currentMouse && !lastMouseDown) {
                                startingMouseDown = info.identifier;
                            }

                            if(mouseDown) {
                                if(!selectedIdentifiers.contains(info.identifier)) {
                                    selectedIdentifiers.add(info.identifier);
                                } else {
                                    selectedIdentifiers.remove(info.identifier);
                                }
                            }

                            break;
                        }
                    }

                    RendererHelper.drawColourOnScreen((int)(255*rMod), (int)(0*gMod), (int)(0*bMod), alpha, posXs[0], posYs[0], widths[0], heights[0], 0D);
                    RendererHelper.drawColourOnScreen((int)(0*rMod), (int)(0*gMod), (int)(255*bMod), alpha, posXs[1], posYs[1], widths[1], heights[1], 0D);
                    RendererHelper.drawColourOnScreen((int)(170*rMod), (int)(0*gMod), (int)(0*bMod), alpha, posXs[2], posYs[2], widths[2], heights[2], 0D);
                    RendererHelper.drawColourOnScreen((int)(0*rMod), (int)(0*gMod), (int)(170*bMod), alpha, posXs[3], posYs[3], widths[3], heights[3], 0D);
                    RendererHelper.drawColourOnScreen((int)(0*rMod), (int)(255*gMod), (int)(0*bMod), alpha, posXs[4], posYs[4], widths[4], heights[4], 0D);
                    RendererHelper.drawColourOnScreen((int)(0*rMod), (int)(170*gMod), (int)(0*bMod), alpha, posXs[5], posYs[5], widths[5], heights[5], 0D);
                }
            }
            for (CubeInfo info : toUpdate) {
                ((GuiWorkspace)this.workspace).updateCube(info);
            }
            if(mouseDown && !mouseOverAny) {
                selectedIdentifiers.clear();
            }

            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.disableBlend();

            RendererHelper.endGlScissor();
            if(this.isTab)
            {
                RendererHelper.startGlScissor(this.posX + 1, this.posY + 1 + 12, this.getWidth() - 2, this.getHeight() - 2 - 12);
            }
            else
            {
                RendererHelper.startGlScissor(this.posX + 1, this.posY + 1, this.getWidth() - 2, this.getHeight() - 2);
            }

            if(imageId == -1)
            {
                workspace.getFontRenderer().drawString(I18n.translateToLocal("window.texture.noTexture"), posX + 4, posY + height - BORDER_SIZE - 12 - 20, Theme.getAsHex(workspace.currentTheme.font), false);
            }
            else if(project.textureFile != null)
            {
                workspace.getFontRenderer().drawString(project.textureFile.getName(), posX + 4, posY + height - BORDER_SIZE - 12 - 20, Theme.getAsHex(workspace.currentTheme.font), false);
            }
            else
            {
                workspace.getFontRenderer().drawString(I18n.translateToLocal("window.texture.remoteTexture"), posX + 4, posY + height - BORDER_SIZE - 12 - 20, Theme.getAsHex(workspace.currentTheme.font), false);
            }

            lastMouseDown = currentMouse;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public void shutdown()
    {
        if(imageId != -1)
        {
            TextureUtil.deleteTexture(imageId);
        }
    }

    @Override
    public boolean interactableWhileNoProjects()
    {
        return false;
    }

    @Override
    public void update()
    {
        super.update();
        if(!((GuiWorkspace)workspace).projectManager.projects.isEmpty())
        {
            ProjectInfo info = ((GuiWorkspace)workspace).projectManager.projects.get(((GuiWorkspace)workspace).projectManager.selectedProject);

            listenTime++;
            if(listenTime > 20)
            {
                listenTime = 0;
                boolean shouldListen = false;
                for(Element e : elements)
                {
                    if(e.id == 0)
                    {
                        shouldListen = ((ElementToggle)e).toggledState;
                    }
                }
                if(shouldListen && info.textureFile != null && info.textureFile.exists())
                {
                    String md5 = IOUtil.getMD5Checksum(info.textureFile);
                    if(md5 != null && !md5.equals(info.textureFileMd5))
                    {
                        info.ignoreNextImage = true;
                        info.textureFileMd5 = md5;

                        BufferedImage image = null;
                        try
                        {
                            image = ImageIO.read(info.textureFile);
                        }
                        catch(IOException e)
                        {
                        }

                        if(!((GuiWorkspace)workspace).remoteSession)
                        {
                            Tabula.proxy.tickHandlerClient.mainframe.loadTexture(info.identifier, image, false);
                        }
                        else if(!((GuiWorkspace)workspace).sessionEnded && ((GuiWorkspace)workspace).isEditor)
                        {
                            ProjectHelper.sendTextureToServer(((GuiWorkspace)workspace).host, info.identifier, false, image);
                        }
                    }
                }
            }

            if(info.bufferedTexture != this.image)
            {
                if(this.imageId != -1)
                {
                    TextureUtil.deleteTexture(this.imageId);
                    this.imageId = -1;
                }
                this.image = info.bufferedTexture;
                if(this.image != null)
                {
                    this.imageId = TextureUtil.uploadTextureImage(TextureUtil.glGenTextures(), this.image);
                }
            }
        }
    }

    @Override
    public void elementTriggered(Element element)
    {
        if(element.id == 1)
        {
            workspace.addWindowOnTop(new WindowLoadTexture(workspace, workspace.width / 2 - 130, workspace.height / 2 - 160, 260, 320, 240, 160));
        }
        if(element.id == 2)
        {
            if(!((GuiWorkspace)workspace).projectManager.projects.isEmpty())
            {
                ProjectInfo info = ((GuiWorkspace)workspace).projectManager.projects.get(((GuiWorkspace)workspace).projectManager.selectedProject);
                if(info.bufferedTexture != null)
                {
                    if(!((GuiWorkspace)workspace).remoteSession)
                    {
                        Tabula.proxy.tickHandlerClient.mainframe.clearTexture(info.identifier);
                    }
                    else if(!((GuiWorkspace)workspace).sessionEnded && ((GuiWorkspace)workspace).isEditor)
                    {
                        Tabula.channel.sendToServer(new PacketClearTexture(((GuiWorkspace)workspace).host, info.identifier));
                    }
                }
            }
        }
    }
}
