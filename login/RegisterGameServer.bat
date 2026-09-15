@echo off
title aCis gameserver registration console
@java -D java.util.logging.config.file=config/console.cfg -cp ./libs/*; ext.mods.gsregistering.GameServerRegister
@pause
