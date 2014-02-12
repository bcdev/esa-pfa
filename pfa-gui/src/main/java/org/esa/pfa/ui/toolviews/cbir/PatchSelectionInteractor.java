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
package org.esa.pfa.ui.toolviews.cbir;

import com.bc.ceres.swing.figure.FigureEditor;
import com.bc.ceres.swing.figure.FigureEditorInteractor;
import com.bc.ceres.swing.figure.ShapeFigure;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;

public class PatchSelectionInteractor extends FigureEditorInteractor {
    private Point referencePoint;
    private boolean canceled;
    private ShapeFigure figure;
    private RectangularShape rectangularShape;
    private boolean started;

    protected RectangularShape createRectangularShape(Point2D point) {
        return new Rectangle2D.Double(point.getX(), point.getY(), 0, 0);
    }

    @Override
    public void cancelInteraction(InputEvent event) {
        if (!canceled) {
            canceled = true;
            getFigureEditor(event).getFigureSelection().removeAllFigures();
            getFigureEditor(event).getFigureCollection().removeFigure(figure);
            super.cancelInteraction(event);
        }
    }

    @Override
    protected void stopInteraction(InputEvent inputEvent) {
        super.stopInteraction(inputEvent);
        started = false;
    }

    @Override
    public void mousePressed(MouseEvent event) {
        started = startInteraction(event);
        if (started) {
            FigureEditor figureEditor = getFigureEditor(event);
            figureEditor.getFigureSelection().removeAllFigures();
            referencePoint = event.getPoint();
            canceled = false;
            rectangularShape = createRectangularShape(toModelPoint(event, referencePoint));
            figure = figureEditor.getFigureFactory().createPolygonFigure(toModelShape(event, rectangularShape),
                                                                         figureEditor.getDefaultPolygonStyle());
            figureEditor.getFigureCollection().addFigure(figure);
        }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (started) {
            FigureEditor figureEditor = getFigureEditor(event);
            if (rectangularShape.isEmpty()) {
                figureEditor.getFigureCollection().removeFigure(figure);
            } else {
                figureEditor.insertFigures(false, figure);
            }
            stopInteraction(event);
        }
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (started) {
            int width = event.getX() - referencePoint.x;
            int height = event.getY() - referencePoint.y;
            int x = referencePoint.x;
            int y = referencePoint.y;
            if (width < 0) {
                width *= -1;
                x -= width;
            }
            if (height < 0) {
                height *= -1;
                y -= height;
            }
            rectangularShape.setFrame(x, y, width, height);
            figure.setShape(getViewToModelTransform(event).createTransformedShape(rectangularShape));
        }
    }
}