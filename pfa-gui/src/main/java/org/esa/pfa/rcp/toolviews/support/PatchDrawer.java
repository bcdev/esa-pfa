/*
 * Copyright (C) 2013 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.pfa.rcp.toolviews.support;

import org.esa.pfa.fe.op.Patch;
import org.esa.pfa.search.CBIRSession;
import org.esa.snap.framework.ui.UIUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * Displays a patch
 */
public class PatchDrawer extends JPanel {

    private final static int imgWidth = 150;
    private final static int imgHeight = 150;
    private final static int margin = 4;
    private final boolean multiRow;

    private static final boolean DEBUG = false;
    private static final Font font = new Font("Ariel", Font.BOLD, 18);

    private static final ImageIcon iconTrue = new ImageIcon(PatchDrawer.class.getClassLoader().getResource("images/check_ball.png"));
    private static final ImageIcon iconFalse = new ImageIcon(PatchDrawer.class.getClassLoader().getResource("images/x_ball.png"));
    private static final ImageIcon iconPatch = new ImageIcon(PatchDrawer.class.getClassLoader().getResource("images/patch.png"));
    private static final Patch[] NO_PATCHES = new Patch[0];
    private PatchContextMenuFactory patchContextMenuFactory;
    private Patch[] patches;
    private final CBIRSession session;

    private enum SelectionMode {CHECK, RECT}

    private SelectionMode selectionMode = SelectionMode.CHECK;

    private PatchDrawing selection = null;

    public PatchDrawer(final CBIRSession session) {
        this(session, false, NO_PATCHES);
    }

    public PatchDrawer(final CBIRSession session, final boolean multiRow, final Patch[] imageList) {
        super(new FlowLayout(FlowLayout.LEADING));
        this.session = session;
        this.patchContextMenuFactory = new PatchContextMenuFactory(session);
        this.multiRow = multiRow;
        update(imageList);
    }

    public void setPatchContextMenuFactory(PatchContextMenuFactory patchContextMenuFactory) {
        this.patchContextMenuFactory = patchContextMenuFactory;
    }

    public List<Patch> getPatches() {
        return Arrays.asList(patches);
    }

    public void update(final Patch[] patches) {
        this.patches = patches;
        this.removeAll();

        if (patches.length == 0) {
            JLabel label = new JLabel();
            label.setIcon(iconPatch);
            this.add(label);
        } else {
            if (multiRow) {
                int numCol = 10;
                int numRow = patches.length / numCol;
                int numImages = session.getImageMode().equals(CBIRSession.ImageMode.DUAL) ? 2 : 1;
                setPreferredSize(new Dimension(((imgWidth * numImages) + margin) * numCol, (imgHeight + margin) * numRow));
            }

            for (Patch patch : patches) {
                final PatchDrawing label = new PatchDrawing(session, patch);
                this.add(label);
            }
        }
        updateUI();
    }

    public class PatchDrawing extends JLabel implements MouseListener {
        private final Patch patch;
        private ImageIcon icon1, icon2;
        private final boolean isDual;
        private final String ql_1, ql_2;

        public PatchDrawing(final CBIRSession session, final Patch patch) {
            this.patch = patch;
            this.isDual = session.getImageMode().equals(CBIRSession.ImageMode.DUAL);
            this.ql_1 = session.getQuicklookBandName1();
            this.ql_2 = session.getQuicklookBandName2();

            if (patch.getImage(ql_1) == null) {
                PatchQuicklookDownloader downloader = new PatchQuicklookDownloader(session, patch, ql_1, this);
                downloader.execute();
            }
            if (patch.getImage(ql_2) == null && isDual) {
                PatchQuicklookDownloader downloader = new PatchQuicklookDownloader(session, patch, ql_2, this);
                downloader.execute();
            }

            update();

            if (isDual) {
                setPreferredSize(new Dimension(imgWidth * 2, imgHeight));
            } else {
                setPreferredSize(new Dimension(imgWidth, imgHeight));
            }
            addMouseListener(this);
        }

        public void update() {
            if (patch.getImage(ql_1) != null) {
                icon1 = new ImageIcon(patch.getImage(ql_1).getScaledInstance(imgWidth, imgHeight, BufferedImage.SCALE_FAST));
            } else {
                icon1 = null;
            }
            if (patch.getImage(ql_2) != null) {
                icon2 = new ImageIcon(patch.getImage(ql_2).getScaledInstance(imgWidth, imgHeight, BufferedImage.SCALE_FAST));
            } else {
                icon2 = null;
            }
        }

        @Override
        public void paintComponent(Graphics graphics) {
            final Graphics2D g = (Graphics2D) graphics;

            if (isDual) {
                drawIcon(g, icon1, 0);
                drawIcon(g, icon2, imgWidth);
                g.setColor(Color.BLUE);
                g.setStroke(new BasicStroke(2));
                g.drawLine(imgWidth, 0, imgWidth, imgHeight);
            } else {
                drawIcon(g, icon1, 0);
            }

            if (DEBUG) {
                g.setColor(Color.WHITE);
                g.fillRect(30, 30, 40, 30);
                g.setColor(Color.RED);
                g.setFont(font);
                g.drawString(Integer.toString(patch.getID()), 35, 50);
            }

            switch (patch.getLabel()) {
                case RELEVANT:
                    g.drawImage(iconTrue.getImage(), 0, 0, null);
                    break;
                case IRRELEVANT:
                    g.drawImage(iconFalse.getImage(), 0, 0, null);
                    break;
            }

            if (this.equals(selection) && selectionMode == SelectionMode.RECT) {
                g.setColor(Color.CYAN);
                g.setStroke(new BasicStroke(5));
                g.drawRoundRect(0, 0, imgWidth, imgHeight - 5, 25, 25);
            }
        }

        private void drawIcon(Graphics2D g, ImageIcon icon, int xOff) {
            if (icon != null && icon.getImage() != null) {
                g.drawImage(icon.getImage(), xOff, 0, imgWidth, imgHeight, null);
            } else {
                // Draw cross to indicate missing image
                g.setColor(Color.DARK_GRAY);
                g.setStroke(new BasicStroke(1));
                g.drawLine(xOff, 0, xOff + imgWidth, imgHeight);
                g.drawLine(xOff + imgWidth, 0, xOff, imgHeight);
                g.drawRect(xOff, 0, imgWidth-1, imgHeight-1);
            }
        }

        /**
         * Invoked when the mouse button has been clicked (pressed
         * and released) on a component.
         */
        @Override
        public void mouseClicked(MouseEvent e) {

        }

        /**
         * Invoked when a mouse button has been pressed on a component.
         */
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                switch (patch.getLabel()) {
                    case RELEVANT:
                        patch.setLabel(Patch.Label.IRRELEVANT);
                        break;
                    case IRRELEVANT:
                    case NONE:
                        patch.setLabel(Patch.Label.RELEVANT);
                        break;
                }
                repaint();
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                JPopupMenu popupMenu = patchContextMenuFactory.createContextMenu(patch);
                if (popupMenu != null) {
                    UIUtils.showPopup(popupMenu, e);
                }
            }
        }

        /**
         * Invoked when a mouse button has been released on a component.
         */
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        /**
         * Invoked when the mouse enters a component.
         */
        @Override
        public void mouseEntered(MouseEvent e) {
        }

        /**
         * Invoked when the mouse exits a component.
         */
        @Override
        public void mouseExited(MouseEvent e) {
        }

    }
}
