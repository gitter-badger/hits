# All rights reserved. (C) Copyright 2009, Trinity College Dublin
# Copyright 2008 Michael Boutros and Ligia P. Bras L and Wolfgang Huber

makePlot = function(path, con, name, w, h=devDims(w)$height, fun, psz=12, print=TRUE, isPlatePlot=FALSE, isImageScreen=FALSE) {

  outf = paste(name, c("pdf", "png"), sep=".")
  nrppi = 72

  pdf(file.path(path, outf[1]), width=w, height=h, pointsize=psz)
  if (isImageScreen) fun(map=FALSE) else fun()
  dev.off()

  if (isPlatePlot) {
  wd = devDims(w)$pwidth
  hg = devDims(w)$pheight
  } else {
  wd = w*nrppi
  hg = h*nrppi
  }

  png(file.path(path, outf[2]), width=wd, height=hg, pointsize=psz)
    res <- fun()
  dev.off()

  if (print)
    cat(sprintf("<CENTER><A HREF=\"%s\"><IMG SRC=\"%s\"/></A></CENTER><BR>\n",
                outf[1], outf[2]), file=con)

  return(res)
}