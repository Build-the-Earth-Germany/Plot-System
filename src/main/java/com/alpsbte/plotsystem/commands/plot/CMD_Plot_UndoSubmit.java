/*
 * The MIT License (MIT)
 *
 *  Copyright © 2021, Alps BTE <bte.atchli@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package com.alpsbte.plotsystem.commands.plot;

import com.alpsbte.plotsystem.commands.BaseCommand;
import com.alpsbte.plotsystem.commands.SubCommand;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.core.system.plot.PlotHandler;
import com.alpsbte.plotsystem.core.system.plot.PlotManager;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.enums.Status;
import com.alpsbte.plotsystem.utils.io.language.LangPaths;
import com.alpsbte.plotsystem.utils.io.language.LangUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.logging.Level;

public class CMD_Plot_UndoSubmit extends SubCommand {

    public CMD_Plot_UndoSubmit(BaseCommand baseCommand) {
        super(baseCommand);
    }

    @Override
    public void onCommand(CommandSender sender, String[] args) {
        try {
            Plot plot;
            if (args.length > 0 && Utils.TryParseInt(args[0]) != null) {
                int plotID = Integer.parseInt(args[0]);
                if (PlotManager.plotExists(plotID)) {
                    plot = new Plot(plotID);
                } else {
                    sender.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(sender, LangPaths.Message.Error.PLOT_DOES_NOT_EXIST)));
                    return;
                }
            } else if (getPlayer(sender) != null && PlotManager.isPlotWorld(getPlayer(sender).getWorld())) {
                plot = PlotManager.getCurrentPlot(Builder.byUUID(getPlayer(sender).getUniqueId()), Status.unreviewed);
            } else {
                sendInfo(sender);
                return;
            }

            if(plot.getStatus() == Status.unreviewed) {
                PlotHandler.undoSubmit(plot);

                sender.sendMessage(Utils.getInfoMessageFormat(LangUtil.get(sender, LangPaths.Message.Info.UNDID_SUBMISSION, plot.getID() + "")));
                if (getPlayer(sender) != null) getPlayer(sender).playSound(getPlayer(sender).getLocation(), Utils.FinishPlotSound, 1, 1);
            } else {
                sender.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(sender, LangPaths.Message.Error.CAN_ONLY_UNDO_SUBMISSIONS_UNREVIEWED_PLOTS)));
            }
        } catch (SQLException ex) {
            sender.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(sender, LangPaths.Message.Error.ERROR_OCCURRED)));
            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
        }
    }

    @Override
    public String[] getNames() {
        return new String[] { "undoSubmit" };
    }

    @Override
    public String getDescription() {
        return "Undo a submission of a unreviewed plot.";
    }

    @Override
    public String[] getParameter() {
        return new String[] { "ID" };
    }

    @Override
    public String getPermission() {
        return "plotsystem.plot.undosubmit";
    }
}
