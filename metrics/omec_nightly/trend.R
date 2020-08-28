# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
# Generates trend graph for omec_nightly job

print( "**********************************************************" )
print( "STEP 1: Data management." )
print( "**********************************************************" )

# Command line arguments are read. Args include the database credentials, test name, branch name, and the directory to output files.
print( "Reading commmand-line args." )
args <- commandArgs(trailingOnly=TRUE)

if (length(args) < 3){
    print("Usage: Rscript trend.R <config-file> <data-file-directory> <output-path-filename>")
    q(status=1)
}

print("Importing libraries.")
library(ggplot2)
library(ggrepel)
library(reshape2)
library(readr)
library(rjson)

config <- fromJSON(file = args[1])
config

outputFile <- args[3]

# Get list of files from data directory
fileList <- list.files(path=args[2], pattern="*.csv")
data <-
  do.call("rbind",
          lapply(fileList,
                 function(x)
                 read.csv(paste(args[2], x, sep=''),
                 stringsAsFactors = FALSE)))

# Use only latest x data determined from config file.
usableData <- tail(data, config$builds_to_show)

# **********************************************************
# STEP 2: Organize data.
# **********************************************************

print( "**********************************************************" )
print( "STEP 2: Organize Data." )
print( "**********************************************************" )

# Exclude "build" column in dataframe
dataFrame <- melt(usableData[names(usableData) != "build"])

# Rename column names in dataFrame
colnames(dataFrame) <- c("Status",
                         "Quantity")

# Add data to new data frame
dataFrame$successful_attach <- usableData$successful_attach
dataFrame$successful_detach <- usableData$successful_detach
dataFrame$failed_attach <- usableData$failed_attach
dataFrame$failed_detach <- usableData$failed_detach
dataFrame$failed_ping <- usableData$failed_ping
dataFrame$display_quantity <- dataFrame$Quantity
dataFrame$build <- usableData$build

# For values that are 0.1, we will display these as 0 on the plot.
for (i in 1:nrow(dataFrame)) {
    if (dataFrame[i, "display_quantity"] == 0.1){
        dataFrame[i, "display_quantity"] <- 0
    }
}

# Adding a temporary iterative list to the dataFrame so that there are no gaps in-between build numbers.

dataFrame$iterative <- seq(0, nrow(usableData) - 1, by=1)
print(dataFrame)

# **********************************************************
# STEP 3: Generate graphs.
# **********************************************************

print( "**********************************************************" )
print( "STEP 3: Generate Plot." )
print( "**********************************************************" )

# -------------------
# Main Plot Formatted
# -------------------

print( "Formatting main plot." )

mainPlot <- ggplot(data=dataFrame, aes(x=iterative,
                                       y=Quantity,
                                       color=Status))

# set the default text size of the graph.
theme_set( theme_grey( base_size = 26 ) )

# geom_ribbon is used so that there is a colored fill below the lines. These values shouldn't be changed.
saColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = successful_attach ),
                             fill = "#0033FF",
                             linetype = 0,
                             alpha = 0.07 )

sdColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = successful_detach ),
                             fill = "#33CC33",
                             linetype = 0,
                             alpha = 0.07 )

faColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_attach ),
                             fill = "#FF0000",
                             linetype = 0,
                             alpha = 0.07 )

fdColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_detach ),
                             fill = "#FF9900",
                             linetype = 0,
                             alpha = 0.07 )

fpColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_ping ),
                             fill = "#CCCC00",
                             linetype = 0,
                             alpha = 0.05 )


# X-axis config
xScaleConfig <- scale_x_continuous( breaks = dataFrame$iterative,
                                   label = dataFrame$build )
# Y-axis config
yAxisTicksExponents <- seq( -1, floor( max( c( max( dataFrame$successful_attach ), max( dataFrame$successful_detach ), max( dataFrame$failed_attach ), max( dataFrame$failed_detach ), max( dataFrame$failed_ping ) ) ) ^ 0.1 ) + 10, by=1 )
yAxisTicks <- 10 ^ yAxisTicksExponents
yAxisTicksLabels <- floor( yAxisTicks )
yScaleConfig <- scale_y_log10( breaks = yAxisTicks,
                               labels = yAxisTicksLabels )

# Axis labels
xLabel <- xlab(config$x_axis_title)
yLabel <- ylab(config$y_axis_title)

# Title of plot
title <- labs( title = config$graph_title, subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

# Other theme options
theme <- theme( plot.title = element_text( hjust = 0.5, size = 32, face ='bold' ),
           axis.text.x = element_text( angle = 0, size = 14 ),
           legend.position = "bottom",
           legend.text = element_text( size = 22 ),
           legend.title = element_blank(),
           legend.key.size = unit( 1.5, 'lines' ),
           legend.direction = 'horizontal',
           plot.subtitle = element_text( size=16, hjust=1.0 ) )

# Wrap legend
wrapLegend <- guides( color = guide_legend( nrow = 2, byrow = TRUE ) )

# Colors for the lines
lineColors <- scale_color_manual( labels = c( "Successful Attach",
                                                   "Successful Detach",
                                                   "Failed Attach",
                                                   "Failed Detach",
                                                   "Failed Ping"),
                                  values=c( "#0028CC",
                                           "#28a328",
                                           "#CC0000",
                                           "#CC6900",
                                           "#888800") )

# Store plot configurations as 1 variable
fundamentalGraphData <- mainPlot +
                        saColor +
                        sdColor +
                        faColor +
                        fdColor +
                        fpColor +
                        xScaleConfig +
                        yScaleConfig +
                        xLabel +
                        yLabel +
                        theme +
                        wrapLegend +
                        lineColors +
                        title

print( "Generating line plot." )

lineGraphFormat <- geom_line( size = 1.1 )
pointFormat <- geom_point( size = 4 )
pointLabel <- geom_text_repel(aes(label=display_quantity),
                              size = 9)

result <- fundamentalGraphData +
           lineGraphFormat +
           pointFormat +
           pointLabel

imageWidth <- 15
imageHeight <- 10
imageDPI <- 200

print("Saving plot...")

ggsave( outputFile,
         width = imageWidth,
         height = imageHeight,
         dpi = imageDPI )

print("Success")
