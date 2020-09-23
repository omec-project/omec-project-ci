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
    print("Usage: Rscript trend.R <config-file> <data-file-directory> <output-path-directory>")
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

outputDirectory <- args[3]

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
usableData$total_ues_attach <- floor(usableData$successful_attach + usableData$failed_attach)
usableData$total_ues_detach <- floor(usableData$successful_detach + usableData$failed_detach)
usableData$total_ues_ping <- floor(usableData$successful_ping + usableData$failed_ping)

# For values that are 0, we will convert them to 0.1 on the plot.
for (i in 1:nrow(usableData)) {
    if (usableData[i, "total_ues_attach"] == 0){
        usableData[i, "total_ues_attach"] <- 0.1
    }
    if (usableData[i, "total_ues_detach"] == 0){
        usableData[i, "total_ues_detach"] <- 0.1
    }
    if (usableData[i, "total_ues_ping"] == 0){
        usableData[i, "total_ues_ping"] <- 0.1
    }
}

# **********************************************************
# STEP 2: Organize data.
# **********************************************************

print( "**********************************************************" )
print( "STEP 2: Organize Data." )
print( "**********************************************************" )

# Exclude "build" column in dataframe
dataFrameAttach <- melt(usableData[c("total_ues_attach", "successful_attach", "failed_attach")])
dataFrameDetach <- melt(usableData[c("total_ues_detach", "successful_detach", "failed_detach")])
dataFramePing <- melt(usableData[c("total_ues_ping", "successful_ping", "failed_ping")])

# Rename column names in dataFrameAttach
colnames(dataFrameAttach) <- c("Status",
                               "Quantity")

# Rename column names in dataFrameDetach
colnames(dataFrameDetach) <- c("Status",
                               "Quantity")

# Rename column names in dataFrameDetach
colnames(dataFramePing) <- c("Status",
                             "Quantity")

# Add data to new data frame
dataFrameAttach$successful_attach <- usableData$successful_attach
dataFrameAttach$failed_attach <- usableData$failed_attach
dataFrameAttach$total_ues <- usableData$total_ues_attach
dataFrameAttach$display_quantity <- dataFrameAttach$Quantity
dataFrameAttach$build <- usableData$build

dataFrameDetach$successful_detach <- usableData$successful_detach
dataFrameDetach$failed_detach <- usableData$failed_detach
dataFrameDetach$total_ues <- usableData$total_ues_detach
dataFrameDetach$display_quantity <- dataFrameDetach$Quantity
dataFrameDetach$build <- usableData$build

dataFramePing$failed_ping <- usableData$failed_ping
dataFramePing$successful_ping <- usableData$successful_ping
dataFramePing$total_ues <- usableData$total_ues_ping
dataFramePing$display_quantity <- dataFramePing$Quantity
dataFramePing$build <- usableData$build

# For values that are 0.1, we will display these as 0 on the plot.
for (i in 1:nrow(dataFrameAttach)) {
    if (dataFrameAttach[i, "display_quantity"] == 0.1){
        dataFrameAttach[i, "display_quantity"] <- 0
    }
}

# For values that are 0.1, we will display these as 0 on the plot.
for (i in 1:nrow(dataFrameDetach)) {
    if (dataFrameDetach[i, "display_quantity"] == 0.1){
        dataFrameDetach[i, "display_quantity"] <- 0
    }
}

# For values that are 0.1, we will display these as 0 on the plot.
for (i in 1:nrow(dataFramePing)) {
    if (dataFramePing[i, "display_quantity"] == 0.1){
        dataFramePing[i, "display_quantity"] <- 0
    }
}

# Adding a temporary iterative list to the dataFrameAttach so that there are no gaps in-between build numbers.
dataFrameAttach$iterative <- seq(0, nrow(usableData) - 1, by=1)
dataFrameDetach$iterative <- seq(0, nrow(usableData) - 1, by=1)
dataFramePing$iterative <- seq(0, nrow(usableData) - 1, by=1)

print(dataFrameAttach)
print(dataFrameDetach)
print(dataFramePing)

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

mainPlotAttach <- ggplot(data=dataFrameAttach, aes(x=iterative,
                                       y=Quantity,
                                       color=Status))

mainPlotDetach <- ggplot(data=dataFrameDetach, aes(x=iterative,
                                       y=Quantity,
                                       color=Status))

mainPlotPing <- ggplot(data=dataFramePing, aes(x=iterative,
                                       y=Quantity,
                                       color=Status))

# set the default text size of the graph.
theme_set( theme_grey( base_size = 26 ) )

# geom_ribbon is used so that there is a colored fill below the lines. These values shouldn't be changed.
saColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = successful_attach ),
                             fill = "#33CC33",
                             linetype = 0,
                             alpha = 0.07 )

taColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = total_ues ),
                             fill = "#3399FF",
                             linetype = 0,
                             alpha = 0.02 )

faColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_attach ),
                             fill = "#FF0000",
                             linetype = 0,
                             alpha = 0.07 )

sdColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = successful_detach ),
                             fill = "#33CC33",
                             linetype = 0,
                             alpha = 0.07 )

fdColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_detach ),
                             fill = "#CC0000",
                             linetype = 0,
                             alpha = 0.07 )

spColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = successful_ping ),
                             fill = "#33CC33",
                             linetype = 0,
                             alpha = 0.07 )

fpColor <- geom_ribbon( aes( ymin = 0,
                             xmin = 0,
                             ymax = failed_ping ),
                             fill = "#FF0000",
                             linetype = 0,
                             alpha = 0.07 )


# X-axis config
xScaleConfig <- scale_x_continuous( breaks = dataFrameAttach$iterative,
                                   label = dataFrameAttach$build )
# Y-axis config
yAxisTicksExponents <- seq( -1, floor( max( dataFrameAttach$total_ues ) ^ 0.1 ) + 10, by=1 )
yAxisTicks <- 10 ^ yAxisTicksExponents
yAxisTicksLabels <- floor( yAxisTicks )
yScaleConfig <- scale_y_log10( breaks = yAxisTicks,
                               labels = yAxisTicksLabels )

# Axis labels
xLabel <- xlab(config$x_axis_title)
yLabel <- ylab(config$y_axis_title)

# Title of plot
title <- labs( title = paste(config$graph_title, "Attach Results"), subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

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
wrapLegend <- guides( color = guide_legend( nrow = 1, byrow = TRUE ) )

# Colors for the lines
lineColors <- scale_color_manual( labels = c( "Total UEs",
                                              "Successful Attach",
                                              "Failed Attach"),
                                  values=c( "#3399FF",
                                            "#33CC33",
                                            "#CC0000" ) )

# Store plot configurations as 1 variable
fundamentalGraphDataAttach <- mainPlotAttach +
                              taColor +
                              saColor +
                              faColor +
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
                              size = 6)

result <- fundamentalGraphDataAttach +
           lineGraphFormat +
           pointFormat +
           pointLabel

imageWidth <- 21
imageHeight <- 9
imageDPI <- 200

print("Saving plot...")

ggsave( paste(outputDirectory, "/attach.png", sep=""),
         width = imageWidth,
         height = imageHeight,
         dpi = imageDPI )

print("Success for Attach")

# Title of plot for Detach
title <- labs( title = paste(config$graph_title, "Detach Results"), subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

# Colors for the lines for Detach
lineColors <- scale_color_manual( labels = c( "Total UEs",
                                              "Successful Detach",
                                              "Failed Detach"),
                                  values=c( "#3399FF",
                                            "#33CC33",
                                            "#CC0000" ) )

# Store plot configurations as 1 variable
fundamentalGraphDataDetach <- mainPlotDetach +
                              taColor +
                              sdColor +
                              fdColor +
                              xScaleConfig +
                              yScaleConfig +
                              xLabel +
                              yLabel +
                              theme +
                              wrapLegend +
                              lineColors +
                              title

result <- fundamentalGraphDataDetach +
           lineGraphFormat +
           pointFormat +
           pointLabel

print("Saving plot...")

ggsave( paste(outputDirectory, "/detach.png", sep=""),
        width = imageWidth,
        height = imageHeight,
        dpi = imageDPI )

print("Success for Detach")

# Title of plot for Ping
title <- labs( title = paste(config$graph_title, "Ping Results"), subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

# Colors for the lines for Ping
lineColors <- scale_color_manual( labels = c( "Total UEs",
                                              "Successful Ping",
                                              "Failed Ping" ),
                                  values=c( "#3399FF",
                                            "#33CC33",
                                            "#CC0000" ) )

# Store plot configurations as 1 variable
fundamentalGraphDataDetach <- mainPlotPing +
                              taColor +
                              spColor +
                              fpColor +
                              xScaleConfig +
                              yScaleConfig +
                              xLabel +
                              yLabel +
                              theme +
                              wrapLegend +
                              lineColors +
                              title

result <- fundamentalGraphDataDetach +
           lineGraphFormat +
           pointFormat +
           pointLabel

print("Saving plot...")

ggsave( paste(outputDirectory, "/ping.png", sep=""),
       width = imageWidth,
       height = imageHeight,
       dpi = imageDPI )

print("Success for Ping")
